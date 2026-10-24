package com.artagram.canvas.community.dto;

import jakarta.validation.constraints.NotBlank;

public record InvitarMiembroRequest(
        @NotBlank(message = "El nombre de artista no puede estar vacío")
        String nombreArtista
) {
}
