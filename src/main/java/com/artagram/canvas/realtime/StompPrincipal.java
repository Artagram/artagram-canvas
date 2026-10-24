package com.artagram.canvas.realtime;

import java.security.Principal;
import java.util.UUID;

/**
 * Principal minimo que queda pegado a la sesion STOMP tras un CONNECT valido
 * (ver StompAuthChannelInterceptor). getName() expone el UUID como string para
 * que los @MessageMapping puedan recuperarlo sin depender de un tipo concreto
 * de Authentication.
 */
public record StompPrincipal(UUID usuarioId, String nombreArtista) implements Principal {

    @Override
    public String getName() {
        return usuarioId.toString();
    }
}
