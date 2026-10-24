package com.artagram.canvas.party;

import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.party.dto.EnviarMensajeChatRequest;
import com.artagram.canvas.party.dto.PartyMensajeChatDto;
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
 * Chat síncrono dentro de una sala de fiesta: a diferencia del chat
 * personal/comunidad (REST + SSE en artagram-chat, sin WebSocket — ver
 * DECISIONES.md #2), la sala ya es una sesión en vivo con rondas
 * cronometradas, así que el chat va por el mismo canal STOMP que el dibujo.
 * <p>
 * No se persiste: ver {@code package-info.java} de este paquete para la
 * decisión y cómo extenderlo a historial si se necesita más adelante.
 * {@link PartyTopicAuthorizer} ya exige participación activa en la sala para
 * cualquier destino bajo {@code /app|/topic/fiesta/{salaId}/...}; aquí se
 * revalida igual, por defensa en profundidad, con el mismo patrón que
 * {@code PartyDrawService}.
 */
@Controller
public class PartyChatStompController {

    private static final Logger log = LoggerFactory.getLogger(PartyChatStompController.class);

    private final ParticipanteSalaRepository participanteRepository;
    private final RealtimeBroadcaster broadcaster;

    public PartyChatStompController(ParticipanteSalaRepository participanteRepository, RealtimeBroadcaster broadcaster) {
        this.participanteRepository = participanteRepository;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/fiesta/{salaId}/chat/enviar")
    public void enviar(@DestinationVariable UUID salaId, @Valid @Payload EnviarMensajeChatRequest request, Principal principal) {
        StompPrincipal p = (StompPrincipal) principal;
        if (!participanteRepository.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(salaId, p.usuarioId())) {
            throw ApiException.forbidden("NO_ERES_PARTICIPANTE", "No perteneces a esta sala");
        }
        PartyMensajeChatDto mensaje = new PartyMensajeChatDto(p.usuarioId(), p.nombreArtista(), request.contenido(), Instant.now());
        broadcaster.enviar("/topic/fiesta/" + salaId + "/chat", mensaje);
    }

    @MessageExceptionHandler
    public void manejarError(Exception ex, Principal principal) {
        log.warn("Error procesando chat de fiesta de {}: {}", principal != null ? principal.getName() : "?", ex.getMessage());
        if (principal != null) {
            broadcaster.enviarAUsuario(principal.getName(), "/queue/errores",
                    Map.of("mensaje", ex.getMessage() == null ? "Solicitud invalida" : ex.getMessage()));
        }
    }
}
