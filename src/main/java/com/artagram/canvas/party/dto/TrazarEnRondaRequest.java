package com.artagram.canvas.party.dto;

import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Puntos del trazo en modo fiesta, con estilo de la herramienta exclusiva
 * tomada. Si no se envia estilo, se usa el asignado por defecto a la ronda.
 */
public record TrazarEnRondaRequest(
        TipoPincel tipoPincel,
        String colorHex,
        Double grosor,
        Double opacidad,
        @NotEmpty @Size(max = 500) List<Punto> puntos
) {
}
