package com.artagram.canvas.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearComunidadRequest(
        @NotBlank @Size(min = 3, max = 80) String nombre,
        @Size(max = 500) String descripcion,
        boolean publica
) {
}
