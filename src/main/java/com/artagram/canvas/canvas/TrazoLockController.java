package com.artagram.canvas.canvas;

import com.artagram.canvas.auth.JwtService;
import com.artagram.canvas.auth.JwtUserResolver;
import com.artagram.canvas.canvas.dto.BloqueoTrazoResponse;
import com.artagram.canvas.canvas.dto.EditarTrazoRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Exclusion mutua por trazo. */
@RestController
@Tag(name = "Lienzo colaborativo", description = "Dibujo en tiempo real, snapshot y bloqueo de trazos")
public class TrazoLockController {

    private final TrazoLockService trazoLockService;
    private final JwtUserResolver userResolver;

    public TrazoLockController(TrazoLockService trazoLockService, JwtUserResolver userResolver) {
        this.trazoLockService = trazoLockService;
        this.userResolver = userResolver;
    }

    @PostMapping("/api/comunidades/{comunidadId}/lienzo/trazos/{trazoId}/bloqueo")
    public ResponseEntity<BloqueoTrazoResponse> tomar(HttpServletRequest request,
                                                       @PathVariable UUID comunidadId, @PathVariable UUID trazoId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        BloqueoTrazoResponse respuesta = trazoLockService.tomar(comunidadId, trazoId, principal.usuarioId(), principal.nombreArtista());
        return ResponseEntity.status(respuesta.tomado() ? HttpStatus.OK : HttpStatus.CONFLICT).body(respuesta);
    }

    @PutMapping("/api/comunidades/{comunidadId}/lienzo/trazos/{trazoId}/bloqueo")
    public ResponseEntity<Void> renovar(HttpServletRequest request,
                                         @PathVariable UUID comunidadId, @PathVariable UUID trazoId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        boolean renovado = trazoLockService.renovar(trazoId, principal.usuarioId(), principal.nombreArtista());
        return renovado ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.GONE).build();
    }

    @DeleteMapping("/api/comunidades/{comunidadId}/lienzo/trazos/{trazoId}/bloqueo")
    public ResponseEntity<Void> liberar(HttpServletRequest request,
                                         @PathVariable UUID comunidadId, @PathVariable UUID trazoId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        trazoLockService.liberar(comunidadId, trazoId, principal.usuarioId(), principal.nombreArtista());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/comunidades/{comunidadId}/lienzo/trazos/{trazoId}")
    public ResponseEntity<Void> editar(HttpServletRequest request,
                                        @PathVariable UUID comunidadId, @PathVariable UUID trazoId,
                                        @Valid @RequestBody EditarTrazoRequest body) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        trazoLockService.editar(comunidadId, trazoId, principal.usuarioId(), principal.nombreArtista(), body);
        return ResponseEntity.ok().build();
    }
}
