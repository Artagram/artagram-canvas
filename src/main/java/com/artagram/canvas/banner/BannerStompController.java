package com.artagram.canvas.banner;

import com.artagram.canvas.banner.dto.BannerTrazoEventoDto;
import com.artagram.canvas.banner.dto.PintarRegionRequest;
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
public class BannerStompController {

    private static final Logger log = LoggerFactory.getLogger(BannerStompController.class);

    private final BannerService bannerService;
    private final RealtimeBroadcaster broadcaster;

    public BannerStompController(BannerService bannerService, RealtimeBroadcaster broadcaster) {
        this.bannerService = bannerService;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/banner/{comunidadId}/pintar")
    public void pintar(@DestinationVariable UUID comunidadId, @Valid @Payload PintarRegionRequest request, Principal principal) {
        StompPrincipal p = (StompPrincipal) principal;
        BannerTrazoEventoDto evento = bannerService.pintar(comunidadId, p.usuarioId(), p.nombreArtista(), null, request);
        broadcaster.enviar("/topic/banner/" + comunidadId, evento);
    }

    @MessageExceptionHandler
    public void manejarError(Exception ex, Principal principal) {
        log.warn("Error procesando mensaje de banner de {}: {}", principal != null ? principal.getName() : "?", ex.getMessage());
        if (principal != null) {
            broadcaster.enviarAUsuario(principal.getName(), "/queue/errores",
                    Map.of("mensaje", ex.getMessage() == null ? "Solicitud invalida" : ex.getMessage()));
        }
    }
}
