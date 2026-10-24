package com.artagram.canvas.banner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * Contador de secuencia independiente por region (HU-16): cada region se
 * serializa aparte, asi que dos regiones distintas nunca se esperan entre si.
 */
@Entity
@Table(name = "banner_regiones", uniqueConstraints = @UniqueConstraint(columnNames = {"banner_id", "region_index"}))
public class BannerRegionEstado {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "banner_id", nullable = false)
    private UUID bannerId;

    @Column(name = "region_index", nullable = false)
    private int regionIndex;

    @Column(name = "contador_secuencia", nullable = false)
    private long contadorSecuencia = 0;

    protected BannerRegionEstado() {
    }

    public BannerRegionEstado(UUID bannerId, int regionIndex) {
        this.bannerId = bannerId;
        this.regionIndex = regionIndex;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBannerId() {
        return bannerId;
    }

    public int getRegionIndex() {
        return regionIndex;
    }

    public long getContadorSecuencia() {
        return contadorSecuencia;
    }

    public long siguienteSecuencia() {
        return ++contadorSecuencia;
    }
}
