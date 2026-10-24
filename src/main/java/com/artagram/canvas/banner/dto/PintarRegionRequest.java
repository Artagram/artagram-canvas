package com.artagram.canvas.banner.dto;

import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Un trazo dentro de UNA region del banner (HU-16). Los puntos son locales a
 * la region (0.0-1.0), no al banner completo, para que una region no tenga
 * que saber nada de las demas.
 */
public record PintarRegionRequest(
        @Min(0) int regionIndex,
        @NotNull TipoPincel tipoPincel,
        @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6,8}$") String colorHex,
        @DecimalMin("0.5") @DecimalMax("128") double grosor,
        @DecimalMin("0.0") @DecimalMax("1.0") double opacidad,
        @NotEmpty @Size(max = 300) List<Punto> puntos
) {
}
