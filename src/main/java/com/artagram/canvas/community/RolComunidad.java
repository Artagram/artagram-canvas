package com.artagram.canvas.community;

public enum RolComunidad {
    ADMIN,
    MODERADOR,
    MIEMBRO;

    public boolean puedeModerar() {
        return this == ADMIN || this == MODERADOR;
    }
}
