package com.artagram.canvas.community.dto;

import java.util.UUID;

public record ComunidadResponse(
        UUID id,
        String nombre,
        String descripcion,
        boolean publica,
        String creador,
        long totalMiembros,
        String miRol
) {
}
