package com.artagram.canvas.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnviarMensajeChatRequest(
        @NotBlank @Size(max = 500) String contenido
) {
}
