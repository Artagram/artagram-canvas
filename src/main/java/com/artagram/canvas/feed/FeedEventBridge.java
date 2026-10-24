package com.artagram.canvas.feed;

import com.artagram.canvas.feed.dto.ComentarioDto;
import com.artagram.canvas.feed.dto.ComentarioEvento;
import com.artagram.canvas.feed.dto.LikeEventoDto;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Retransmite por STOMP los eventos de likes/comentarios que publica
 * artagram-feed, para que {@code PostCard.tsx} siga viendo los contadores
 * actualizarse en vivo sin que el frontend tenga que cambiar nada (mismos
 * destinos: /topic/publicaciones/{id}/likes y /comentarios).
 * <p>
 * <b>Ojo:</b> el equivalente en ARTAGRAM-backv1 ({@code LienzoEventBridge})
 * lee los campos del payload con nombres que NO coinciden con los que
 * realmente publica {@code FeedEventPublisher} ({@code postId}/{@code totalLikes}/
 * {@code totalComentarios} en vez de {@code publicacionId}/{@code contadorLikes}/
 * {@code contadorComentarios}): ahí el {@code UUID.fromString} revienta,
 * se traga la excepción y el evento nunca llega a STOMP. Es un bug real y
 * ya activo en el monolito (confirmado disparando un like real y viendo el
 * WARN en sus logs), no algo introducido por esta migración — se corrigió
 * también ahí de paso. Aquí se usan los nombres reales desde el principio.
 */
@Component
public class FeedEventBridge {

    private static final Logger log = LoggerFactory.getLogger(FeedEventBridge.class);

    private final RealtimeBroadcaster broadcaster;

    public FeedEventBridge(RealtimeBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @RabbitListener(queues = FeedEventsRabbitMqConfig.QUEUE_LIKE_UPDATED)
    public void onLikeUpdated(Map<String, Object> payload) {
        try {
            UUID publicacionId = UUID.fromString(String.valueOf(payload.get("publicacionId")));
            int contadorLikes = ((Number) payload.getOrDefault("contadorLikes", 0)).intValue();
            String destino = "/topic/publicaciones/" + publicacionId + "/likes";
            broadcaster.enviar(destino, new LikeEventoDto("LIKES_ACTUALIZADOS", publicacionId, contadorLikes));
        } catch (Exception e) {
            log.warn("Error retransmitiendo like.updated a STOMP: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = FeedEventsRabbitMqConfig.QUEUE_COMMENT_CREATED)
    public void onCommentCreated(Map<String, Object> payload) {
        try {
            UUID comentarioId = UUID.fromString(String.valueOf(payload.get("comentarioId")));
            UUID publicacionId = UUID.fromString(String.valueOf(payload.get("publicacionId")));
            UUID autorId = UUID.fromString(String.valueOf(payload.get("autorId")));
            String autorNombre = String.valueOf(payload.getOrDefault("autorNombre", "artista"));
            String contenido = String.valueOf(payload.getOrDefault("contenido", ""));
            int contadorComentarios = ((Number) payload.getOrDefault("contadorComentarios", 0)).intValue();
            Instant creadoEn = payload.get("timestamp") != null
                    ? Instant.parse(String.valueOf(payload.get("timestamp")))
                    : Instant.now();

            ComentarioDto dto = new ComentarioDto(comentarioId, publicacionId, autorId, autorNombre, contenido, creadoEn);
            String destino = "/topic/publicaciones/" + publicacionId + "/comentarios";
            broadcaster.enviar(destino, new ComentarioEvento("COMENTARIO_CREADO", dto, contadorComentarios));
        } catch (Exception e) {
            log.warn("Error retransmitiendo comment.created a STOMP: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = FeedEventsRabbitMqConfig.QUEUE_COMMENT_DELETED)
    public void onCommentDeleted(Map<String, Object> payload) {
        try {
            UUID publicacionId = UUID.fromString(String.valueOf(payload.get("publicacionId")));
            UUID comentarioId = UUID.fromString(String.valueOf(payload.get("comentarioId")));
            int contadorComentarios = ((Number) payload.getOrDefault("contadorComentarios", 0)).intValue();
            ComentarioDto dto = new ComentarioDto(comentarioId, publicacionId, null, "", "", Instant.now());
            String destino = "/topic/publicaciones/" + publicacionId + "/comentarios";
            broadcaster.enviar(destino, new ComentarioEvento("COMENTARIO_ELIMINADO", dto, contadorComentarios));
        } catch (Exception e) {
            log.warn("Error retransmitiendo comment.deleted a STOMP: {}", e.getMessage());
        }
    }
}
