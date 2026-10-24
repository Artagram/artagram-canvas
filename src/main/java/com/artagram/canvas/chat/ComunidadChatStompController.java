package com.artagram.canvas.chat;

import com.artagram.canvas.chat.dto.EnviarMensajeComunidadRequest;
import com.artagram.canvas.chat.dto.MensajeComunidadDto;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.realtime.StompPrincipal;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Chat de comunidad en vivo (migrado de ARTAGRAM-backv1): el permiso se
 * revisa en CADA mensaje, no solo al conectarse, para que a alguien recien
 * expulsado no le siga llegando ni dejandole escribir — igual que antes.
 * <p>
 * A diferencia del monolito, aqui no hay persistencia local: se transmite en
 * vivo y se publica {@code room.message} para que artagram-chat (dueño real
 * de la persistencia) lo guarde. Ver {@link ChatEventPublisher}.
 */
@Controller
public class ComunidadChatStompController {

    private static final Logger log = LoggerFactory.getLogger(ComunidadChatStompController.class);

    private final ComunidadService comunidadService;
    private final RealtimeBroadcaster broadcaster;
    private final ChatEventPublisher eventPublisher;

    public ComunidadChatStompController(ComunidadService comunidadService, RealtimeBroadcaster broadcaster,
                                         ChatEventPublisher eventPublisher) {
        this.comunidadService = comunidadService;
        this.broadcaster = broadcaster;
        this.eventPublisher = eventPublisher;
    }

    @MessageMapping("/comunidad/{comunidadId}/chat/enviar")
    public void enviar(@DestinationVariable UUID comunidadId, @Valid @Payload EnviarMensajeComunidadRequest request,
                        Principal principal) {
        StompPrincipal p = (StompPrincipal) principal;
        if (!comunidadService.esMiembroActivo(p.usuarioId(), comunidadId)) {
            throw ApiException.forbidden("SIN_PERMISO", "No perteneces a esta comunidad (o te expulsaron)");
        }
        MensajeComunidadDto dto = new MensajeComunidadDto(UUID.randomUUID(), p.usuarioId(), p.nombreArtista(),
                request.contenido(), Instant.now());
        broadcaster.enviar("/topic/comunidad/" + comunidadId + "/chat", dto);
        eventPublisher.publicarMensajeComunidad(comunidadId, dto);
    }

    @MessageExceptionHandler
    public void manejarError(Exception ex, Principal principal) {
        log.warn("Error procesando mensaje de comunidad de {}: {}", principal != null ? principal.getName() : "?", ex.getMessage());
        if (principal != null) {
            broadcaster.enviarAUsuario(principal.getName(), "/queue/errores",
                    Map.of("mensaje", ex.getMessage() == null ? "Solicitud invalida" : ex.getMessage()));
        }
    }
}
