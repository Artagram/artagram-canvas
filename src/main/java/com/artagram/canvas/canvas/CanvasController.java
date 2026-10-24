package com.artagram.canvas.canvas;

import com.artagram.canvas.auth.JwtService;
import com.artagram.canvas.auth.JwtUserResolver;
import com.artagram.canvas.canvas.dto.TrazoDto;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.community.ComunidadService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Snapshot REST para quien llega tarde al lienzo: trae todo lo ya dibujado en
 * una sola respuesta y de ahi en adelante solo le llega lo nuevo por STOMP.
 */
@RestController
@Tag(name = "Lienzo colaborativo", description = "Dibujo en tiempo real, snapshot y bloqueo de trazos")
public class CanvasController {

    private final CanvasService canvasService;
    private final ComunidadService comunidadService;
    private final JwtUserResolver userResolver;

    public CanvasController(CanvasService canvasService, ComunidadService comunidadService,
                             JwtUserResolver userResolver) {
        this.canvasService = canvasService;
        this.comunidadService = comunidadService;
        this.userResolver = userResolver;
    }

    @GetMapping("/api/comunidades/{comunidadId}/lienzo/trazos")
    public List<TrazoDto> obtenerTrazos(HttpServletRequest request, @PathVariable UUID comunidadId,
                                         @RequestParam(defaultValue = "0") long desde) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        if (!comunidadService.esMiembroActivo(principal.usuarioId(), comunidadId)) {
            throw ApiException.forbidden("NO_ERES_MIEMBRO", "No perteneces a esta comunidad");
        }
        return canvasService.obtenerTrazosDesde(comunidadId, desde).stream().map(TrazoDto::de).toList();
    }
}
