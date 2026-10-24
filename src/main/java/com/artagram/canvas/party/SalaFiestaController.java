package com.artagram.canvas.party;

import com.artagram.canvas.auth.JwtService;
import com.artagram.canvas.auth.JwtUserResolver;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.common.lock.DistributedLockService;
import com.artagram.canvas.party.dto.CrearSalaRequest;
import com.artagram.canvas.party.dto.EstadoRondaResponse;
import com.artagram.canvas.party.dto.SalaFiestaResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Crear/entrar a salas de fiesta y disparo de rondas. */
@RestController
@Tag(name = "Modo fiesta", description = "Salas, rondas cronometradas y borrador disputado")
public class SalaFiestaController {

    private final SalaFiestaService salaFiestaService;
    private final DistributedLockService lockService;
    private final JwtUserResolver userResolver;

    public SalaFiestaController(SalaFiestaService salaFiestaService, DistributedLockService lockService,
                                 JwtUserResolver userResolver) {
        this.salaFiestaService = salaFiestaService;
        this.lockService = lockService;
        this.userResolver = userResolver;
    }

    @PostMapping("/api/fiestas")
    public ResponseEntity<SalaFiestaResponse> crear(HttpServletRequest request, @Valid @RequestBody CrearSalaRequest body) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(salaFiestaService.crear(principal, body));
    }

    @PostMapping("/api/fiestas/unirse")
    public SalaFiestaResponse unirse(HttpServletRequest request, @RequestParam String codigo) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        return salaFiestaService.unirse(principal, codigo);
    }

    @PostMapping("/api/fiestas/{salaId}/rondas")
    public ResponseEntity<Void> iniciarRonda(HttpServletRequest request, @PathVariable UUID salaId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        salaFiestaService.iniciarRonda(principal.usuarioId(), salaId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/fiestas/activas")
    public List<SalaFiestaResponse> salasActivas(HttpServletRequest request) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        return salaFiestaService.listarSalasActivasDelUsuario(principal.usuarioId());
    }

    @DeleteMapping("/api/fiestas/{salaId}")
    public ResponseEntity<Void> cerrarSala(HttpServletRequest request, @PathVariable UUID salaId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        salaFiestaService.cerrarSala(principal.usuarioId(), salaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/fiestas/{salaId}/salir")
    public ResponseEntity<Void> salirDeSala(HttpServletRequest request, @PathVariable UUID salaId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        salaFiestaService.salirDeSala(principal.usuarioId(), salaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/fiestas/limpiar")
    public ResponseEntity<Map<String, Object>> limpiarSalas(HttpServletRequest request) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        int cantidad = salaFiestaService.limpiarSalasInactivas(principal.usuarioId());
        return ResponseEntity.ok(Map.of("cerradas", cantidad));
    }

    @GetMapping("/api/fiestas/{salaId}")
    public SalaFiestaResponse ver(HttpServletRequest request, @PathVariable UUID salaId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        if (!salaFiestaService.esParticipanteActivo(principal.usuarioId(), salaId)) {
            throw ApiException.forbidden("NO_ERES_PARTICIPANTE", "No perteneces a esta sala");
        }
        return salaFiestaService.obtenerRespuesta(salaId);
    }

    @GetMapping("/api/fiestas/{salaId}/estado")
    public EstadoRondaResponse estado(HttpServletRequest request, @PathVariable UUID salaId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        if (!salaFiestaService.esParticipanteActivo(principal.usuarioId(), salaId)) {
            throw ApiException.forbidden("NO_ERES_PARTICIPANTE", "No perteneces a esta sala");
        }
        String propietarioBorrador = lockService.propietarioActual("fiesta-borrador:" + salaId)
                .map(p -> p.substring(p.indexOf("::") + 2))
                .orElse(null);
        return salaFiestaService.obtenerEstado(salaId, propietarioBorrador);
    }
}
