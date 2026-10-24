package com.artagram.canvas.banner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * El banner de 1000x1000 de una comunidad, particionado en regiones cuadradas
 * (HU-16). El bitmap consolidado y las regiones individuales se agregan en el
 * modulo de concurrencia sobre el lienzo.
 */
@Entity
@Table(name = "banners_comunitarios")
public class BannerComunitario {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "comunidad_id", nullable = false, unique = true)
    private UUID comunidadId;

    @Column(nullable = false)
    private int tamano = 1000;

    @Column(name = "tamano_region", nullable = false)
    private int tamanoRegion = 100;

    protected BannerComunitario() {
    }

    public BannerComunitario(UUID comunidadId, int tamano, int tamanoRegion) {
        this.comunidadId = comunidadId;
        this.tamano = tamano;
        this.tamanoRegion = tamanoRegion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getComunidadId() {
        return comunidadId;
    }

    public int getTamano() {
        return tamano;
    }

    public int getTamanoRegion() {
        return tamanoRegion;
    }
}
