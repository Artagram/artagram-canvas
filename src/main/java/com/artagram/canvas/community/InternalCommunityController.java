package com.artagram.canvas.community;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno (bloqueado hacia afuera por artagram-gateway) para que
 * otros servicios (hoy: artagram-chat, vía su propio {@code LienzoClient})
 * consulten membresía sin duplicar la lógica. Mismo contrato que el
 * equivalente en ARTAGRAM-backv1.
 */
@RestController
public class InternalCommunityController {

    private final ComunidadRepository comunidadRepository;
    private final ComunidadService comunidadService;

    public InternalCommunityController(ComunidadRepository comunidadRepository, ComunidadService comunidadService) {
        this.comunidadRepository = comunidadRepository;
        this.comunidadService = comunidadService;
    }

    @GetMapping("/internal/communities/{comunidadId}/members/{usuarioId}")
    public ResponseEntity<Map<String, Object>> verificarMembresia(
            @PathVariable UUID comunidadId, @PathVariable UUID usuarioId) {
        Optional<Comunidad> comunidadOpt = comunidadRepository.findById(comunidadId);
        if (comunidadOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Comunidad comunidad = comunidadOpt.get();
        Optional<RolComunidad> rolOpt = comunidadService.obtenerRol(usuarioId, comunidadId);
        boolean miembroActivo = rolOpt.isPresent();
        return ResponseEntity.ok(Map.of(
                "comunidadId", comunidadId.toString(),
                "usuarioId", usuarioId.toString(),
                "miembroActivo", miembroActivo,
                "rol", rolOpt.map(Enum::name).orElse("NINGUNO"),
                "comunidadPublica", comunidad.isPublica()
        ));
    }
}
