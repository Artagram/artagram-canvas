package com.artagram.canvas.party;

import com.artagram.canvas.auth.JwtService;
import com.artagram.canvas.auth.JwtUserResolver;
import com.artagram.canvas.party.dto.BloqueoBorradorResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exclusion mutua sobre el borrador del modo fiesta. */
@RestController
@Tag(name = "Modo fiesta", description = "Salas, rondas cronometradas y borrador disputado")
public class PartyBorradorController {

    private final PartyDrawService partyDrawService;
    private final JwtUserResolver userResolver;

    public PartyBorradorController(PartyDrawService partyDrawService, JwtUserResolver userResolver) {
        this.partyDrawService = partyDrawService;
        this.userResolver = userResolver;
    }

    @PostMapping("/api/fiestas/{salaId}/borrador")
    public ResponseEntity<BloqueoBorradorResponse> tomar(HttpServletRequest request, @PathVariable UUID salaId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        BloqueoBorradorResponse respuesta = partyDrawService.tomarBorrador(salaId, principal.usuarioId(), principal.nombreArtista());
        return ResponseEntity.status(respuesta.tomado() ? HttpStatus.OK : HttpStatus.CONFLICT).body(respuesta);
    }

    @DeleteMapping("/api/fiestas/{salaId}/borrador")
    public ResponseEntity<Void> liberar(HttpServletRequest request, @PathVariable UUID salaId) {
        JwtService.TokenPrincipal principal = userResolver.resolverRequerido(request);
        partyDrawService.liberarBorrador(salaId, principal.usuarioId(), principal.nombreArtista());
        return ResponseEntity.noContent().build();
    }
}
