package com.artagram.canvas.party;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "party_ronda_estado", uniqueConstraints = @UniqueConstraint(columnNames = {"sala_id", "numero_ronda"}))
public class PartyRondaEstado {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "sala_id", nullable = false)
    private UUID salaId;

    @Column(name = "numero_ronda", nullable = false)
    private int numeroRonda;

    @Column(name = "contador_secuencia", nullable = false)
    private long contadorSecuencia = 0;

    protected PartyRondaEstado() {
    }

    public PartyRondaEstado(UUID salaId, int numeroRonda) {
        this.salaId = salaId;
        this.numeroRonda = numeroRonda;
    }

    public UUID getSalaId() {
        return salaId;
    }

    public int getNumeroRonda() {
        return numeroRonda;
    }

    public long siguienteSecuencia() {
        return ++contadorSecuencia;
    }
}
