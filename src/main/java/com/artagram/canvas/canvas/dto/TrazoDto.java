package com.artagram.canvas.canvas.dto;

import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import com.artagram.canvas.canvas.Trazo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrazoDto(
        UUID id,
        long secuencia,
        UUID autorId,
        String autorNombre,
        TipoPincel tipoPincel,
        String colorHex,
        double grosor,
        double opacidad,
        int capa,
        List<Punto> puntos,
        Instant creadoEn
) {
    public static TrazoDto de(Trazo trazo) {
        return new TrazoDto(trazo.getId(), trazo.getSecuencia(), trazo.getAutor().getUsuarioId(),
                trazo.getAutor().getNombreArtista(), trazo.getTipoPincel(), trazo.getColorHex(),
                trazo.getGrosor(), trazo.getOpacidad(), trazo.getCapa(), trazo.getPuntos(), trazo.getCreadoEn());
    }
}
