package com.artagram.canvas.canvas;

/**
 * Catalogo de pinceles version 1, compartido entre servidor y cliente. Si se
 * agregan mas, el cliente viejo que no los reconozca debe caer al basico (esa
 * logica de repliegue vive en el frontend, que es quien la sufre).
 */
public enum TipoPincel {
    LAPIZ,
    MARCADOR,
    ACUARELA,
    PLUMA,
    AEROGRAFO
}
