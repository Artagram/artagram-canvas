package com.artagram.canvas.realtime;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Revalida el permiso real (ej. ser participante activo de una sala) en cada
 * SUBSCRIBE/SEND. La autenticacion del JWT ya paso en JwtHandshakeInterceptor
 * antes de subir a WebSocket; aqui solo llega trafico de sesiones ya autenticadas.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final List<DestinationAuthorizer> authorizers;

    public StompAuthChannelInterceptor(List<DestinationAuthorizer> authorizers) {
        this.authorizers = authorizers;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (StompCommand.SUBSCRIBE.equals(command) || StompCommand.SEND.equals(command)) {
            String destino = accessor.getDestination();
            if (destino != null && !destino.startsWith("/user/")) {
                StompPrincipal principal = (StompPrincipal) accessor.getUser();
                if (principal == null) {
                    throw new StompAuthException("Sesion no autenticada");
                }
                boolean autorizado = authorizers.stream()
                        .filter(a -> a.soporta(destino))
                        .findFirst()
                        .map(a -> a.autorizado(principal, destino))
                        .orElse(true);
                if (!autorizado) {
                    log.warn("Suscripcion/mensaje rechazado para usuario={} destino={}", principal.usuarioId(), destino);
                    throw new StompAuthException("No tienes permiso sobre " + destino);
                }
            }
        }
        return message;
    }
}
