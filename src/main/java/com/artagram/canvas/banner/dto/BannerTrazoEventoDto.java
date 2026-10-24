package com.artagram.canvas.banner.dto;

import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import java.util.List;
import java.util.UUID;

public record BannerTrazoEventoDto(
        int regionIndex,
        long secuencia,
        UUID autorId,
        String autorNombre,
        TipoPincel tipoPincel,
        String colorHex,
        double grosor,
        double opacidad,
        List<Punto> puntos
) {
}
