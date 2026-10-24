package com.artagram.canvas.chat;

import com.artagram.canvas.chat.dto.EnviarMensajePersonalRequest;
import com.artagram.canvas.chat.dto.MensajeComunidadDto;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica hacia artagram-chat (dueño de la persistencia de mensajes) los
 * eventos que resultan de un mensaje recibido por STOMP aqui. Canvas no
 * guarda copia local de ningun mensaje de chat.
 * <p>
 * {@code room.message} ya existe como contrato (lo publicaba
 * ARTAGRAM-backv1 vía {@code LienzoEventBridge.publicarMensajeComunidad},
 * mismo exchange/routing-key/payload) — artagram-chat ya lo consume desde
 * antes, así que reusarlo aqui no requiere ningun cambio en artagram-chat.
 * {@code personal.message.sent} es nuevo: no existia ningun camino de chat
 * personal por STOMP hasta ahora (solo REST), así que el listener que lo
 * consume se agrega en este mismo cambio dentro de artagram-chat.
 */
@Component
public class ChatEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public ChatEventPublisher(RabbitTemplate rabbitTemplate,
                               @Value("${app.rabbitmq.exchange:artagram.events}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    public void publicarMensajeComunidad(UUID comunidadId, MensajeComunidadDto dto) {
        Map<String, Object> payload = Map.of(
                "id", dto.id().toString(),
                "comunidadId", comunidadId.toString(),
                "autorId", dto.remitenteId().toString(),
                "autorNombre", dto.remitenteNombre(),
                "contenido", dto.contenido(),
                "creadoEn", dto.creadoEn().toString()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, "room.message", payload);
        } catch (Exception e) {
            log.warn("No se pudo publicar room.message: {}", e.getMessage());
        }
    }

    public void publicarMensajePersonal(UUID mensajeId, UUID remitenteId, String remitenteNombre,
                                         EnviarMensajePersonalRequest request) {
        Map<String, Object> payload = Map.of(
                "id", mensajeId.toString(),
                "remitenteId", remitenteId.toString(),
                "remitenteNombre", remitenteNombre,
                "destinatarioId", request.destinatarioId().toString(),
                "contenido", request.contenido()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, "personal.message.sent", payload);
        } catch (Exception e) {
            log.warn("No se pudo publicar personal.message.sent: {}", e.getMessage());
        }
    }
}
