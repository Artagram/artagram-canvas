package com.artagram.canvas.feed.dto;

public record ComentarioEvento(
        String tipo,
        ComentarioDto comentario,
        int contadorComentarios
) {
}
