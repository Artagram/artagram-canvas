package com.artagram.canvas.realtime;

import com.artagram.canvas.common.tracing.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Reparte los mensajes de tiempo real (lienzo, banner, fiesta, chat de fiesta)
 * entre TODAS las instancias del servicio, para que escalar a mas de una
 * instancia detras de un load balancer no rompa nada: un {@code SimpleBroker}
 * (ver {@link WebSocketConfig}) solo entrega a las sesiones WebSocket
 * conectadas a ESA misma instancia, asi que sin esto un cliente conectado a
 * la instancia A nunca veria un trazo dibujado por alguien en la instancia B.
 * <p>
 * En vez de un broker relay externo, reusamos el Redis que el servicio ya
 * tiene para locks/rate-limiting: publicamos cada envio en un {@link RTopic}
 * y cada instancia (incluida la que publico) lo recibe y hace la entrega
 * local de verdad con {@link SimpMessagingTemplate}.
 * <p>
 * Cada envio lleva la hora en que se publico y el traceId de quien lo
 * origino: al entregarlo, cada replica mide cuanto tardo en atravesar Redis
 * ({@code trazo_propagacion_seconds}, escenarios 1 y 10) y deja en el log las
 * entregas lentas con su traceId para seguirlas por los servicios.
 */
@Component
public class RealtimeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RealtimeBroadcaster.class);
    private static final String CANAL = "artagram:canvas-stomp-relay";

    private enum TipoEnvio { TOPICO, USUARIO }

    /**
     * El payload viaja ya serializado a JSON y se recibe como un {@code Object}
     * generico en vez de reconstruir el tipo Java original: el conversor de
     * salida de STOMP lo va a volver a serializar a JSON de todas formas antes
     * de mandarlo al navegador, asi que el JSON final es identico.
     */
    private record RelayEnvelope(TipoEnvio tipo, String destino, String usuarioId, String payloadJson,
                                 long emitidoEnMs, String traceId) {
    }

    /** Por encima de esto una entrega cuenta como lenta (p95 objetivo de los escenarios 1 y 10). */
    static final long UMBRAL_LENTO_MS = 200;

    private final RedissonClient redissonClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry registry;
    private RTopic topic;

    /**
     * El aviso de entrega lenta sale como mucho una vez por segundo, con cuantas
     * hubo en ese lapso. Antes salia uno por mensaje: con el lienzo atrasado eso
     * eran miles de lineas por segundo, el log (sincrono) frenaba mas la entrega
     * y el atraso crecia sin freno.
     */
    static final long INTERVALO_AVISO_LENTO_MS = 1000;
    private final AtomicLong ultimoAvisoLentoMs = new AtomicLong();
    private final AtomicLong lentasSinAvisar = new AtomicLong();

    public RealtimeBroadcaster(RedissonClient redissonClient, SimpMessagingTemplate messagingTemplate,
                               ObjectMapper objectMapper, MeterRegistry registry) {
        this.redissonClient = redissonClient;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    @PostConstruct
    void suscribirse() {
        topic = redissonClient.getTopic(CANAL);
        topic.addListener(RelayEnvelope.class, (canal, envelope) -> entregarLocal(envelope));
    }

    /** Reemplaza {@code SimpMessagingTemplate.convertAndSend(destino, payload)}. */
    public void enviar(String destino, Object payload) {
        publicar(new RelayEnvelope(TipoEnvio.TOPICO, destino, null, serializar(payload),
                System.currentTimeMillis(), traceIdActual()));
    }

    /** Reemplaza {@code SimpMessagingTemplate.convertAndSendToUser(usuarioId, destino, payload)}. */
    public void enviarAUsuario(String usuarioId, String destino, Object payload) {
        publicar(new RelayEnvelope(TipoEnvio.USUARIO, destino, usuarioId, serializar(payload),
                System.currentTimeMillis(), traceIdActual()));
    }

    private void publicar(RelayEnvelope envelope) {
        topic.publish(envelope);
    }

    private void entregarLocal(RelayEnvelope envelope) {
        if (envelope.traceId() != null) {
            MDC.put(TraceContext.TRACE_ID_MDC_KEY, envelope.traceId());
        }
        try {
            medirPropagacion(envelope);
            Object payload = objectMapper.readValue(envelope.payloadJson(), Object.class);
            if (envelope.tipo() == TipoEnvio.USUARIO) {
                messagingTemplate.convertAndSendToUser(envelope.usuarioId(), envelope.destino(), payload);
            } else {
                messagingTemplate.convertAndSend(envelope.destino(), payload);
            }
        } catch (Exception e) {
            log.error("No se pudo entregar un mensaje de tiempo real relayado: destino={}", envelope.destino(), e);
        } finally {
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }

    private void medirPropagacion(RelayEnvelope envelope) {
        if (envelope.emitidoEnMs() <= 0) {
            return;
        }
        long ms = Math.max(0, System.currentTimeMillis() - envelope.emitidoEnMs());
        String canal = canal(envelope);
        Timer.builder("trazo_propagacion")
                .description("Tiempo desde que una replica publica un evento de tiempo real hasta que cada replica lo entrega")
                .tag("canal", canal)
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(50), Duration.ofMillis(100),
                        Duration.ofMillis(UMBRAL_LENTO_MS), Duration.ofMillis(500))
                .register(registry)
                .record(Duration.ofMillis(ms));
        if (ms > UMBRAL_LENTO_MS) {
            avisarLenta(canal, envelope, ms);
        }
    }

    private void avisarLenta(String canal, RelayEnvelope envelope, long ms) {
        long lentas = lentasSinAvisar.incrementAndGet();
        long ahora = System.currentTimeMillis();
        long ultimo = ultimoAvisoLentoMs.get();
        if (ahora - ultimo < INTERVALO_AVISO_LENTO_MS || !ultimoAvisoLentoMs.compareAndSet(ultimo, ahora)) {
            return;
        }
        lentasSinAvisar.addAndGet(-lentas);
        log.warn("Entrega de tiempo real lenta: canal={} destino={} ms={} traceId={} lentasEnElUltimoSegundo={}",
                canal, envelope.destino(), ms, envelope.traceId(), lentas);
    }

    /** lienzo, banner, fiesta o usuario: pocos valores, para no disparar la cardinalidad. */
    static String canal(String destino, boolean aUsuario) {
        if (aUsuario) {
            return "usuario";
        }
        String[] partes = destino == null ? new String[0] : destino.split("/");
        return partes.length > 2 && "topic".equals(partes[1]) ? partes[2] : "otro";
    }

    private static String canal(RelayEnvelope envelope) {
        return canal(envelope.destino(), envelope.tipo() == TipoEnvio.USUARIO);
    }

    private static String traceIdActual() {
        String traceId = MDC.get(TraceContext.TRACE_ID_MDC_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String serializar(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo serializar el payload de tiempo real: " + payload.getClass(), e);
        }
    }
}
