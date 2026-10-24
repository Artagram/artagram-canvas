package com.artagram.canvas.party;

import com.artagram.canvas.party.dto.TrazarEnRondaRequest;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.realtime.StompPrincipal;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class PartyStompController {

    private static final Logger log = LoggerFactory.getLogger(PartyStompController.class);

    private final PartyDrawService partyDrawService;
    private final RealtimeBroadcaster broadcaster;

    public PartyStompController(PartyDrawService partyDrawService, RealtimeBroadcaster broadcaster) {
        this.partyDrawService = partyDrawService;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/fiesta/{salaId}/trazo")
    public void trazar(@DestinationVariable UUID salaId, @Valid @Payload TrazarEnRondaRequest request, Principal principal) {
        StompPrincipal p = (StompPrincipal) principal;
        partyDrawService.trazar(salaId, p.usuarioId(), p.nombreArtista(), request);
    }

    @MessageMapping("/fiesta/{salaId}/parcial")
    public void parcial(@DestinationVariable UUID salaId, @Valid @Payload TrazarEnRondaRequest request, Principal principal) {
        StompPrincipal p = (StompPrincipal) principal;
        partyDrawService.parcial(salaId, p.usuarioId(), p.nombreArtista(), request);
    }

    @MessageMapping("/fiesta/{salaId}/borrar")
    public void borrar(@DestinationVariable UUID salaId, @Valid @Payload TrazarEnRondaRequest request, Principal principal) {
        StompPrincipal p = (StompPrincipal) principal;
        partyDrawService.borrar(salaId, p.usuarioId(), p.nombreArtista(), request);
    }

    @MessageExceptionHandler
    public void manejarError(Exception ex, Principal principal) {
        log.warn("Error procesando mensaje de fiesta de {}: {}", principal != null ? principal.getName() : "?", ex.getMessage());
        if (principal != null) {
            broadcaster.enviarAUsuario(principal.getName(), "/queue/errores",
                    Map.of("mensaje", ex.getMessage() == null ? "Solicitud invalida" : ex.getMessage()));
        }
    }
}
