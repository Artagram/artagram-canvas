package com.artagram.canvas.canvas.dto;

import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EditarTrazoRequest(
        @NotNull TipoPincel tipoPincel,
        @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6,8}$") String colorHex,
        @DecimalMin("0.5") @DecimalMax("128") double grosor,
        @DecimalMin("0.0") @DecimalMax("1.0") double opacidad,
        int capa,
        @NotEmpty @Size(max = 500) List<Punto> puntos
) {
}
