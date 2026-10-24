package com.artagram.canvas.canvas;

import com.artagram.canvas.canvas.dto.EnviarPuntosRequest;
import com.artagram.canvas.canvas.dto.TrazoEventoDto;
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

/**
 * Canal de dibujo colaborativo. El SUBSCRIBE a /topic/lienzo/{id} ya lo
 * filtra {@code ComunidadTopicAuthorizer}; aqui ademas se revalida en cada envio.
 */
@Controller
public class CanvasStompController {

    private static final Logger log = LoggerFactory.getLogger(CanvasStompController.class);

    private final CanvasService canvasService;
    private final RealtimeBroadcaster broadcaster;

    public CanvasStompController(CanvasService canvasService, RealtimeBroadcaster broadcaster) {
        this.canvasService = canvasService;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/lienzo/{comunidadId}/parcial")
    public void recibirParcial(@DestinationVariable UUID comunidadId,
                                @Valid @Payload EnviarPuntosRequest request,
                                Principal principal) {
        StompPrincipal p = (StompPrincipal) principal;
        TrazoEventoDto evento = canvasService.procesarParcial(comunidadId, p.usuarioId(), p.nombreArtista(), request);
        broadcaster.enviar("/topic/lienzo/" + comunidadId, evento);
    }

    @MessageMapping("/lienzo/{comunidadId}/final")
    public void recibirFinal(@DestinationVariable UUID comunidadId,
                              @Valid @Payload EnviarPuntosRequest request,
                              Principal principal) {
        long inicio = System.nanoTime();
        StompPrincipal p = (StompPrincipal) principal;
        TrazoEventoDto evento = canvasService.finalizarTrazo(comunidadId, p.usuarioId(), p.nombreArtista(), request);
        broadcaster.enviar("/topic/lienzo/" + comunidadId, evento);
        canvasService.registrarLatencia((System.nanoTime() - inicio) / 1_000_000);
    }

    @MessageExceptionHandler
    public void manejarError(Exception ex, Principal principal) {
        log.warn("Error procesando mensaje de lienzo de {}: {}", principal != null ? principal.getName() : "?", ex.getMessage());
        if (principal != null) {
            broadcaster.enviarAUsuario(principal.getName(), "/queue/errores",
                    Map.of("mensaje", ex.getMessage() == null ? "Solicitud invalida" : ex.getMessage()));
        }
    }
}
