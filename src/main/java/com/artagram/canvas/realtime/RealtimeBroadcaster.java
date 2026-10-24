package com.artagram.canvas.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private record RelayEnvelope(TipoEnvio tipo, String destino, String usuarioId, String payloadJson) {
    }

    private final RedissonClient redissonClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private RTopic topic;

    public RealtimeBroadcaster(RedissonClient redissonClient, SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void suscribirse() {
        topic = redissonClient.getTopic(CANAL);
        topic.addListener(RelayEnvelope.class, (canal, envelope) -> entregarLocal(envelope));
    }

    /** Reemplaza {@code SimpMessagingTemplate.convertAndSend(destino, payload)}. */
    public void enviar(String destino, Object payload) {
        publicar(new RelayEnvelope(TipoEnvio.TOPICO, destino, null, serializar(payload)));
    }

    /** Reemplaza {@code SimpMessagingTemplate.convertAndSendToUser(usuarioId, destino, payload)}. */
    public void enviarAUsuario(String usuarioId, String destino, Object payload) {
        publicar(new RelayEnvelope(TipoEnvio.USUARIO, destino, usuarioId, serializar(payload)));
    }

    private void publicar(RelayEnvelope envelope) {
        topic.publish(envelope);
    }

    private void entregarLocal(RelayEnvelope envelope) {
        try {
            Object payload = objectMapper.readValue(envelope.payloadJson(), Object.class);
            if (envelope.tipo() == TipoEnvio.USUARIO) {
                messagingTemplate.convertAndSendToUser(envelope.usuarioId(), envelope.destino(), payload);
            } else {
                messagingTemplate.convertAndSend(envelope.destino(), payload);
            }
        } catch (Exception e) {
            log.error("No se pudo entregar un mensaje de tiempo real relayado: destino={}", envelope.destino(), e);
        }
    }

    private String serializar(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo serializar el payload de tiempo real: " + payload.getClass(), e);
        }
    }
}
