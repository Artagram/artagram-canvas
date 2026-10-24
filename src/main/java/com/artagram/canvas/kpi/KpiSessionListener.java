package com.artagram.canvas.kpi;

import com.artagram.canvas.realtime.StompPrincipal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * Deduce cuando alguien entra y sale de un lienzo compartido o una sala de
 * fiesta a partir de las suscripciones STOMP (mismo criterio que usaba
 * ARTAGRAM-backv1 para KPI-1/KPI-2 de HU-37), y lo reporta a artagram-users
 * via {@link KpiEventPublisher} — el calculo del KPI ya no vive aqui. El mapa
 * es en memoria de este proceso a proposito: una sesion WebSocket vive entera
 * en UNA sola instancia, asi que no hace falta Redis para esto.
 */
@Component
public class KpiSessionListener {

    private static final Pattern DESTINO_COLABORATIVO = Pattern.compile("^/topic/(lienzo|fiesta)/[0-9a-fA-F-]{36}$");

    private final KpiEventPublisher publisher;
    private final Map<String, Instant> inicioPorSesion = new ConcurrentHashMap<>();

    public KpiSessionListener(KpiEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener
    public void alSuscribirse(SessionSubscribeEvent event) {
        if (!(event.getUser() instanceof StompPrincipal principal)) {
            return;
        }
        String destino = SimpMessageHeaderAccessor.wrap(event.getMessage()).getDestination();
        if (destino == null || !DESTINO_COLABORATIVO.matcher(destino).matches()) {
            return;
        }
        String sesionId = SimpMessageHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (sesionId != null && inicioPorSesion.putIfAbsent(sesionId, Instant.now()) == null) {
            publisher.publicarSesionIniciada(principal.usuarioId());
        }
    }

    @EventListener
    public void alDesconectar(SessionDisconnectEvent event) {
        if (!(event.getUser() instanceof StompPrincipal principal)) {
            return;
        }
        Instant inicio = inicioPorSesion.remove(event.getSessionId());
        if (inicio != null) {
            publisher.publicarSesionTerminada(principal.usuarioId(), Instant.now().toEpochMilli() - inicio.toEpochMilli());
        }
    }
}
