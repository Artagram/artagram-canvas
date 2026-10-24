package com.artagram.canvas.canvas;

/**
 * Coordenada normalizada (0.0 a 1.0) relativa al lienzo, para que el trazo se
 * vea igual sin importar la resolucion de pantalla de cada cliente.
 */
public record Punto(double x, double y) {
}
