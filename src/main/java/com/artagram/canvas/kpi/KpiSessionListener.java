package com.artagram.canvas.kpi;

import com.artagram.canvas.realtime.StompPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Deduce cuando alguien entra y sale de un lienzo compartido o una sala de
 * fiesta a partir de las suscripciones STOMP, y lo reporta via
 * {@link KpiEventPublisher}. Se sigue cada suscripcion y no la conexion: el
 * frontend usa un solo WebSocket para toda la app, asi que salir del lienzo
 * solo cancela la suscripcion y el socket queda abierto. El mapa es en memoria
 * de este proceso a proposito: una sesion WebSocket vive entera en UNA sola
 * instancia, asi que no hace falta Redis para esto.
 */
@Component
public class KpiSessionListener {

    private static final Pattern DESTINO_COLABORATIVO = Pattern.compile("^/topic/(lienzo|fiesta)/([0-9a-fA-F-]{36})$");

    private record Presencia(String actividadId, String tipo, UUID recursoId, Instant inicio) {
    }

    private final KpiEventPublisher publisher;
    private final Map<String, Map<String, Presencia>> presenciasPorSesion = new ConcurrentHashMap<>();

    public KpiSessionListener(KpiEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener
    public void alSuscribirse(SessionSubscribeEvent event) {
        if (!(event.getUser() instanceof StompPrincipal principal)) {
            return;
        }
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String destino = headers.getDestination();
        String sesionId = headers.getSessionId();
        String suscripcionId = headers.getSubscriptionId();
        Matcher colaborativo = destino == null ? null : DESTINO_COLABORATIVO.matcher(destino);
        if (colaborativo == null || !colaborativo.matches() || sesionId == null || suscripcionId == null) {
            return;
        }
        Presencia presencia = new Presencia(sesionId + ":" + suscripcionId, colaborativo.group(1).toUpperCase(),
                UUID.fromString(colaborativo.group(2)), Instant.now());
        presenciasPorSesion.computeIfAbsent(sesionId, id -> new ConcurrentHashMap<>()).put(suscripcionId, presencia);
        publisher.publicarSesionIniciada(principal.usuarioId(), presencia.actividadId(), presencia.tipo(), presencia.recursoId());
    }

    @EventListener
    public void alDesuscribirse(SessionUnsubscribeEvent event) {
        if (!(event.getUser() instanceof StompPrincipal principal)) {
            return;
        }
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Presencia> presencias = presenciasPorSesion.get(headers.getSessionId());
        if (presencias == null || headers.getSubscriptionId() == null) {
            return;
        }
        Presencia presencia = presencias.remove(headers.getSubscriptionId());
        if (presencia != null) {
            terminar(principal, presencia);
        }
    }

    @EventListener
    public void alDesconectar(SessionDisconnectEvent event) {
        if (!(event.getUser() instanceof StompPrincipal principal)) {
            return;
        }
        Map<String, Presencia> presencias = presenciasPorSesion.remove(event.getSessionId());
        if (presencias != null) {
            List.copyOf(presencias.values()).forEach(p -> terminar(principal, p));
        }
    }

    private void terminar(StompPrincipal principal, Presencia presencia) {
        long duracionMs = Instant.now().toEpochMilli() - presencia.inicio().toEpochMilli();
        publisher.publicarSesionTerminada(principal.usuarioId(), presencia.actividadId(), duracionMs);
    }
}
