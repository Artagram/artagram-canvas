package com.artagram.canvas.realtime;

/**
 * Cada modulo con canales en tiempo real (lienzo, banner, fiesta...) aporta uno
 * de estos para que StompAuthChannelInterceptor decida si un SUBSCRIBE/SEND se
 * deja pasar. La suscripcion es el punto donde se revalida el permiso real, no
 * solo la interfaz.
 */
public interface DestinationAuthorizer {

    boolean soporta(String destino);

    boolean autorizado(StompPrincipal principal, String destino);
}
