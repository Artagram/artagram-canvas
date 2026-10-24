package com.artagram.canvas.community;

import com.artagram.canvas.auth.JwtService;
import com.artagram.canvas.auth.JwtUserResolver;
import com.artagram.canvas.community.dto.CambiarRolRequest;
import com.artagram.canvas.community.dto.ComunidadResponse;
import com.artagram.canvas.community.dto.CrearComunidadRequest;
import com.artagram.canvas.community.dto.InvitarMiembroRequest;
import com.artagram.canvas.community.dto.MiembroComunidadDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Comunidades", description = "Crear/unirse, roles y moderación")
public class ComunidadController {

    private final ComunidadService comunidadService;
    private final JwtUserResolver userResolver;

    public ComunidadController(ComunidadService comunidadService, JwtUserResolver userResolver) {
        this.comunidadService = comunidadService;
        this.userResolver = userResolver;
    }

    @PostMapping("/api/comunidades")
    public ResponseEntity<ComunidadResponse> crear(HttpServletRequest request, @Valid @RequestBody CrearComunidadRequest body) {
        JwtService.TokenPrincipal p = userResolver.resolverRequerido(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(comunidadService.crear(p.usuarioId(), p.nombreArtista(), p.premium(), body));
    }

    @PostMapping("/api/comunidades/{id}/miembros")
    public ComunidadResponse unirse(HttpServletRequest request, @PathVariable UUID id) {
        JwtService.TokenPrincipal p = userResolver.resolverRequerido(request);
        return comunidadService.unirse(p.usuarioId(), p.nombreArtista(), id);
    }

    @GetMapping("/api/comunidades/{id}/miembros")
    public List<MiembroComunidadDto> listarMiembros(HttpServletRequest request, @PathVariable UUID id) {
        JwtService.TokenPrincipal p = userResolver.resolverRequerido(request);
        return comunidadService.listarMiembros(p.usuarioId(), p.rolGlobal(), id);
    }

    @PostMapping("/api/comunidades/{id}/miembros/invitar")
    public MiembroComunidadDto invitar(HttpServletRequest request, @PathVariable UUID id,
                                        @Valid @RequestBody InvitarMiembroRequest body) {
        JwtService.TokenPrincipal p = userResolver.resolverRequerido(request);
        return comunidadService.invitarMiembro(p.usuarioId(), p.rolGlobal(), id, body.nombreArtista());
    }

    @GetMapping("/api/comunidades/mias")
    public List<ComunidadResponse> misComunidades(HttpServletRequest request) {
        JwtService.TokenPrincipal p = userResolver.resolverRequerido(request);
        return comunidadService.misComunidades(p.usuarioId());
    }

    @GetMapping("/api/comunidades/{id}")
    public ComunidadResponse ver(HttpServletRequest request, @PathVariable UUID id) {
        UUID usuarioId = userResolver.resolverOpcional(request).map(JwtService.TokenPrincipal::usuarioId).orElse(null);
        return comunidadService.ver(id, usuarioId);
    }

    @PatchMapping("/api/comunidades/{id}/miembros/{usuarioObjetivoId}/rol")
    public ResponseEntity<Void> cambiarRol(HttpServletRequest request, @PathVariable UUID id,
                                            @PathVariable UUID usuarioObjetivoId, @Valid @RequestBody CambiarRolRequest body) {
        JwtService.TokenPrincipal p = userResolver.resolverRequerido(request);
        comunidadService.cambiarRol(p.usuarioId(), p.rolGlobal(), id, usuarioObjetivoId, body.rol());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/comunidades/{id}/miembros/{usuarioObjetivoId}")
    public ResponseEntity<Void> expulsar(HttpServletRequest request, @PathVariable UUID id,
                                          @PathVariable UUID usuarioObjetivoId) {
        JwtService.TokenPrincipal p = userResolver.resolverRequerido(request);
        comunidadService.expulsar(p.usuarioId(), p.rolGlobal(), id, usuarioObjetivoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/comunidades/{id}")
    public ResponseEntity<Void> eliminar(HttpServletRequest request, @PathVariable UUID id) {
        JwtService.TokenPrincipal p = userResolver.resolverRequerido(request);
        comunidadService.eliminar(p.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
