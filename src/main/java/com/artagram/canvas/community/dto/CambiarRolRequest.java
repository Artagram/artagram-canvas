package com.artagram.canvas.community.dto;

import com.artagram.canvas.community.RolComunidad;
import jakarta.validation.constraints.NotNull;

public record CambiarRolRequest(@NotNull RolComunidad rol) {
}
