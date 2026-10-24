package com.artagram.canvas.realtime;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Convierte lo que dejo JwtHandshakeInterceptor en los atributos del handshake
 * en el Principal de la sesion WebSocket. Spring propaga este Principal a
 * accessor.getUser() en TODOS los frames STOMP de la sesion, no solo el primero.
 */
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    @Nullable
    protected Principal determineUser(@NonNull ServerHttpRequest request,
                                       @NonNull WebSocketHandler wsHandler,
                                       @NonNull Map<String, Object> attributes) {
        Object usuarioId = attributes.get(JwtHandshakeInterceptor.ATTR_USUARIO_ID);
        Object nombreArtista = attributes.get(JwtHandshakeInterceptor.ATTR_NOMBRE_ARTISTA);
        if (usuarioId == null) {
            return null;
        }
        return new StompPrincipal(UUID.fromString((String) usuarioId), (String) nombreArtista);
    }
}
