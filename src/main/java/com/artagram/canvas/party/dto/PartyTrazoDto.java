package com.artagram.canvas.party.dto;

import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import java.util.List;
import java.util.UUID;

public record PartyTrazoDto(
        long secuencia,
        UUID autorId,
        String autorNombre,
        boolean borrador,
        TipoPincel tipoPincel,
        String colorHex,
        double grosor,
        double opacidad,
        List<Punto> puntos
) {
}
