package com.artagram.canvas.banner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Bitmap PNG consolidado de una region (HU-16, nota tecnica): cargar el banner
 * no implica reproducir todo el historial de trazos, solo este PNG mas los
 * trazos posteriores a ultimaSecuenciaIncluida.
 */
@Entity
@Table(name = "banner_snapshots", uniqueConstraints = @UniqueConstraint(columnNames = {"banner_id", "region_index"}))
public class BannerSnapshot {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "banner_id", nullable = false)
    private UUID bannerId;

    @Column(name = "region_index", nullable = false)
    private int regionIndex;

    @Column(nullable = false)
    private byte[] png;

    @Column(name = "ultima_secuencia_incluida", nullable = false)
    private long ultimaSecuenciaIncluida;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected BannerSnapshot() {
    }

    public BannerSnapshot(UUID bannerId, int regionIndex, byte[] png, long ultimaSecuenciaIncluida) {
        this.bannerId = bannerId;
        this.regionIndex = regionIndex;
        this.png = png;
        this.ultimaSecuenciaIncluida = ultimaSecuenciaIncluida;
    }

    public UUID getBannerId() {
        return bannerId;
    }

    public int getRegionIndex() {
        return regionIndex;
    }

    public byte[] getPng() {
        return png;
    }

    public long getUltimaSecuenciaIncluida() {
        return ultimaSecuenciaIncluida;
    }

    public void actualizar(byte[] png, long ultimaSecuenciaIncluida) {
        this.png = png;
        this.ultimaSecuenciaIncluida = ultimaSecuenciaIncluida;
        this.actualizadoEn = Instant.now();
    }
}
