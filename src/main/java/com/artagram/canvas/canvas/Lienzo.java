package com.artagram.canvas.canvas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Un lienzo por comunidad. El contador de secuencia vive aqui y se actualiza
 * de forma atomica en la misma transaccion que inserta cada trazo, para que
 * el orden de dibujo sea consistente sin depender de un sistema aparte.
 */
@Entity
@Table(name = "lienzos")
public class Lienzo {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "comunidad_id", nullable = false, unique = true)
    private UUID comunidadId;

    @Column(name = "contador_secuencia", nullable = false)
    private long contadorSecuencia = 0;

    protected Lienzo() {
    }

    public Lienzo(UUID comunidadId) {
        this.comunidadId = comunidadId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getComunidadId() {
        return comunidadId;
    }

    public long getContadorSecuencia() {
        return contadorSecuencia;
    }

    public long siguienteSecuencia() {
        return ++contadorSecuencia;
    }
}
