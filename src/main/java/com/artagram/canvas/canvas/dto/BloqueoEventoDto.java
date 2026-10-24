package com.artagram.canvas.canvas.dto;

import java.util.UUID;

public record BloqueoEventoDto(String tipo, UUID trazoId, String propietarioNombre) {
}
