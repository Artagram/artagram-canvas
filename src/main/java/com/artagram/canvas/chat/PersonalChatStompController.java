package com.artagram.canvas.chat;

import com.artagram.canvas.chat.dto.EnviarMensajePersonalRequest;
import com.artagram.canvas.chat.dto.MensajePersonalDto;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.realtime.StompPrincipal;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Chat personal (1:1) en vivo (migrado de ARTAGRAM-backv1). Sin persistencia
 * local — se transmite en vivo a ambos extremos y se publica
 * {@code personal.message.sent} para que artagram-chat lo guarde (ver
 * {@link ChatEventPublisher}).
 * <p>
 * A diferencia del monolito, no se valida aqui que el destinatario exista:
 * canvas solo tiene una proyeccion parcial de usuarios (los que interactuaron
 * con comunidad/fiesta/lienzo), no un directorio completo — validar
 * existencia del destinatario queda en artagram-chat, que si lo tiene.
 */
@Controller
public class PersonalChatStompController {

    private static final Logger log = LoggerFactory.getLogger(PersonalChatStompController.class);

    private final RealtimeBroadcaster broadcaster;
    private final ChatEventPublisher eventPublisher;

    public PersonalChatStompController(RealtimeBroadcaster broadcaster, ChatEventPublisher eventPublisher) {
        this.broadcaster = broadcaster;
        this.eventPublisher = eventPublisher;
    }

    @MessageMapping("/mensajes/enviar")
    public void enviar(@Valid @Payload EnviarMensajePersonalRequest request, Principal principal) {
        StompPrincipal p = (StompPrincipal) principal;
        if (p.usuarioId().equals(request.destinatarioId())) {
            throw ApiException.badRequest("DESTINATARIO_INVALIDO", "No puedes enviarte mensajes a ti mismo");
        }
        UUID mensajeId = UUID.randomUUID();
        MensajePersonalDto dto = new MensajePersonalDto(mensajeId, p.usuarioId(), p.nombreArtista(),
                request.destinatarioId(), request.contenido(), true, Instant.now());

        broadcaster.enviarAUsuario(request.destinatarioId().toString(), "/queue/mensajes", dto);
        broadcaster.enviarAUsuario(p.usuarioId().toString(), "/queue/mensajes", dto);
        eventPublisher.publicarMensajePersonal(mensajeId, p.usuarioId(), p.nombreArtista(), request);
    }

    @MessageExceptionHandler
    public void manejarError(Exception ex, Principal principal) {
        log.warn("Error procesando mensaje personal de {}: {}", principal != null ? principal.getName() : "?", ex.getMessage());
        if (principal != null) {
            broadcaster.enviarAUsuario(principal.getName(), "/queue/errores",
                    Map.of("mensaje", ex.getMessage() == null ? "Solicitud invalida" : ex.getMessage()));
        }
    }
}
