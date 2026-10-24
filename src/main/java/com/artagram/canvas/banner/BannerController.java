package com.artagram.canvas.banner;

import com.artagram.canvas.auth.JwtService;
import com.artagram.canvas.auth.JwtUserResolver;
import com.artagram.canvas.banner.dto.BannerTrazoEventoDto;
import com.artagram.canvas.banner.dto.PintarRegionRequest;
import com.artagram.canvas.banner.dto.RegionEstadoDto;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ademas del canal STOMP (BannerStompController), se deja un endpoint REST
 * equivalente para pintar: util para pruebas de carga/concurrencia y para
 * clientes que no quieran mantener una sesion WebSocket solo para esto.
 */
@RestController
@Tag(name = "Banner mural", description = "HU-16: mural comunitario 1000×1000 particionado por regiones")
public class BannerController {

    private final BannerService bannerService;
    private final ComunidadService comunidadService;
    private final RealtimeBroadcaster broadcaster;
    private final JwtUserResolver userResolver;

    public BannerController(BannerService bannerService, ComunidadService comunidadService,
                             RealtimeBroadcaster broadcaster, JwtUserResolver userResolver) {
        this.bannerService = bannerService;
        this.comunidadService = comunidadService;
        this.broadcaster = broadcaster;
        this.userResolver = userResolver;
    }

    @GetMapping("/api/comunidades/{comunidadId}/banner")
    public List<RegionEstadoDto> obtenerEstado(HttpServletRequest request, @PathVariable UUID comunidadId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        if (!comunidadService.esMiembroActivo(principal.usuarioId(), comunidadId)) {
            var com = comunidadService.ver(comunidadId, principal.usuarioId());
            boolean esGlobalAdmin = "ADMIN".equalsIgnoreCase(principal.rolGlobal());
            if (!com.publica() && !esGlobalAdmin) {
                throw ApiException.forbidden("NO_ERES_MIEMBRO", "No perteneces a esta comunidad");
            }
        }
        return bannerService.obtenerEstado(comunidadId);
    }

    @PostMapping("/api/comunidades/{comunidadId}/banner/pintar")
    public ResponseEntity<Void> pintar(HttpServletRequest request, @PathVariable UUID comunidadId,
                                        @Valid @RequestBody PintarRegionRequest requestBody) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        BannerTrazoEventoDto evento = bannerService.pintar(comunidadId, principal.usuarioId(), principal.nombreArtista(),
                principal.rolGlobal(), requestBody);
        broadcaster.enviar("/topic/banner/" + comunidadId, evento);
        return ResponseEntity.ok().build();
    }
}
