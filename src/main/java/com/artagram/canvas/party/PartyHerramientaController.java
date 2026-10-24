package com.artagram.canvas.party;

import com.artagram.canvas.auth.JwtService;
import com.artagram.canvas.auth.JwtUserResolver;
import com.artagram.canvas.party.dto.BloqueoHerramientaResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Modo fiesta", description = "Herramientas exclusivas por participante durante la ronda")
public class PartyHerramientaController {

    private final PartyDrawService partyDrawService;
    private final JwtUserResolver userResolver;

    public PartyHerramientaController(PartyDrawService partyDrawService, JwtUserResolver userResolver) {
        this.partyDrawService = partyDrawService;
        this.userResolver = userResolver;
    }

    @PostMapping("/api/fiestas/{salaId}/herramientas/{herramienta}")
    public ResponseEntity<BloqueoHerramientaResponse> tomar(HttpServletRequest request, @PathVariable UUID salaId,
                                                             @PathVariable String herramienta) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        BloqueoHerramientaResponse respuesta = partyDrawService.tomarHerramienta(salaId, principal.usuarioId(), principal.nombreArtista(), herramienta);
        return ResponseEntity.status(respuesta.tomado() ? HttpStatus.OK : HttpStatus.CONFLICT).body(respuesta);
    }

    @DeleteMapping("/api/fiestas/{salaId}/herramientas/{herramienta}")
    public ResponseEntity<Void> liberar(HttpServletRequest request, @PathVariable UUID salaId,
                                         @PathVariable String herramienta) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        partyDrawService.liberarHerramienta(salaId, principal.usuarioId(), principal.nombreArtista(), herramienta);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/fiestas/{salaId}/herramientas")
    public Map<String, String> consultar(@PathVariable UUID salaId) {
        return partyDrawService.consultarHerramientas(salaId);
    }
}
