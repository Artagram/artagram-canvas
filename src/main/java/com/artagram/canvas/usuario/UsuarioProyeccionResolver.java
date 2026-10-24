package com.artagram.canvas.usuario;

import com.artagram.canvas.auth.JwtService;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La proyección de usuario se sincroniza de forma asíncrona vía RabbitMQ
 * (ver {@link UsuarioProyeccionEventListener}), así que puede no existir aún
 * cuando un usuario recién registrado hace su primera acción en este servicio.
 * Como defensa, si falta, se crea al vuelo con los claims que ya trae el JWT
 * (el mismo issuer firma ambos), en vez de bloquear la operación.
 */
@Component
public class UsuarioProyeccionResolver {

    private final UsuarioProyeccionRepository repository;

    public UsuarioProyeccionResolver(UsuarioProyeccionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UsuarioProyeccion resolver(JwtService.TokenPrincipal principal) {
        return resolver(principal.usuarioId(), principal.nombreArtista(), principal.premium());
    }

    /**
     * Para flujos STOMP: {@code StompPrincipal} solo carga usuarioId y
     * nombreArtista (no premium), así que se asume {@code false} por defecto
     * si hay que crear la proyección al vuelo — la sincronización real de
     * premium llega poco después vía {@code subscription.updated}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UsuarioProyeccion resolver(UUID usuarioId, String nombreArtista) {
        return resolver(usuarioId, nombreArtista, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UsuarioProyeccion resolver(UUID usuarioId, String nombreArtista, boolean premium) {
        return repository.findById(usuarioId)
                .orElseGet(() -> repository.save(new UsuarioProyeccion(usuarioId, nombreArtista, premium)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UsuarioProyeccion referencia(UUID usuarioId) {
        return repository.getReferenceById(usuarioId);
    }
}
