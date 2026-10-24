package com.artagram.canvas.usuario;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantiene {@code usuarios_proyeccion} al día a partir de los eventos que
 * publican artagram-auth y artagram-users. Es puramente informativa: si un
 * evento llega tarde o se pierde, {@link UsuarioProyeccionResolver} crea la
 * fila que falte con los claims del propio JWT en el momento en que se
 * necesite.
 */
@Component
public class UsuarioProyeccionEventListener {

    private static final Logger log = LoggerFactory.getLogger(UsuarioProyeccionEventListener.class);

    private final UsuarioProyeccionRepository repository;

    public UsuarioProyeccionEventListener(UsuarioProyeccionRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = CanvasUserEventsRabbitMqConfig.QUEUE_USER_REGISTERED)
    @Transactional
    public void onUserRegistered(Map<String, Object> payload) {
        try {
            UUID usuarioId = UUID.fromString(String.valueOf(payload.get("usuarioId")));
            String nombreArtista = String.valueOf(payload.getOrDefault("nombreArtista", "artista"));
            boolean premium = Boolean.TRUE.equals(payload.get("premium"));
            repository.findById(usuarioId).ifPresentOrElse(existente -> {
                existente.setNombreArtista(nombreArtista);
                existente.setPremium(premium);
                repository.save(existente);
            }, () -> repository.save(new UsuarioProyeccion(usuarioId, nombreArtista, premium)));
            log.info("Usuario sincronizado en artagram-canvas: id={} nombreArtista={}", usuarioId, nombreArtista);
        } catch (Exception e) {
            log.warn("Error sincronizando user.registered en artagram-canvas: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = CanvasUserEventsRabbitMqConfig.QUEUE_SUBSCRIPTION_UPDATED)
    @Transactional
    public void onSubscriptionUpdated(Map<String, Object> payload) {
        try {
            UUID usuarioId = UUID.fromString(String.valueOf(payload.get("usuarioId")));
            boolean premium = Boolean.TRUE.equals(payload.get("premium"));
            repository.findById(usuarioId).ifPresent(u -> {
                u.setPremium(premium);
                repository.save(u);
            });
            log.info("Estado premium sincronizado en artagram-canvas: usuarioId={} premium={}", usuarioId, premium);
        } catch (Exception e) {
            log.warn("Error sincronizando subscription.updated en artagram-canvas: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = CanvasUserEventsRabbitMqConfig.QUEUE_USER_DELETED)
    @Transactional
    public void onUserDeleted(Map<String, Object> payload) {
        try {
            UUID usuarioId = UUID.fromString(String.valueOf(payload.get("usuarioId")));
            // No se borra la fila: salas/trazos ya existentes siguen necesitando
            // el autorId para historial. Si se requiere anonimizar, se hace aquí.
            log.info("user.deleted recibido en artagram-canvas para usuarioId={} (sin acción, se conserva historial)", usuarioId);
        } catch (Exception e) {
            log.warn("Error procesando user.deleted en artagram-canvas: {}", e.getMessage());
        }
    }
}
