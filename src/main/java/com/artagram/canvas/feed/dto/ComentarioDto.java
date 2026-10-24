package com.artagram.canvas.feed.dto;

import java.time.Instant;
import java.util.UUID;

public record ComentarioDto(
        UUID id,
        UUID publicacionId,
        UUID autorId,
        String autorNombre,
        String contenido,
        Instant creadoEn
) {
}
