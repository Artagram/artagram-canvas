package com.artagram.canvas.party.dto;

public record BloqueoHerramientaResponse(
        boolean tomado,
        String herramienta,
        String propietarioNombre
) {
}
