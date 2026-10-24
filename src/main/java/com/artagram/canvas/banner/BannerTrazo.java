package com.artagram.canvas.banner;

import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.PuntosConverter;
import com.artagram.canvas.canvas.TipoPincel;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Trazo dentro de UNA region del banner (coordenadas locales 0.0-1.0 relativas
 * al tamano de esa region, no al banner completo).
 */
@Entity
@Table(name = "banner_trazos")
public class BannerTrazo {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "banner_id", nullable = false)
    private UUID bannerId;

    @Column(name = "region_index", nullable = false)
    private int regionIndex;

    @Column(nullable = false)
    private long secuencia;

    @ManyToOne(optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private UsuarioProyeccion autor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pincel", nullable = false, length = 20)
    private TipoPincel tipoPincel;

    @Column(name = "color_hex", nullable = false, length = 9)
    private String colorHex;

    @Column(nullable = false)
    private double grosor;

    @Column(nullable = false)
    private double opacidad;

    @Convert(converter = PuntosConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<Punto> puntos;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected BannerTrazo() {
    }

    public BannerTrazo(UUID bannerId, int regionIndex, long secuencia, UsuarioProyeccion autor, TipoPincel tipoPincel,
                        String colorHex, double grosor, double opacidad, List<Punto> puntos) {
        this.bannerId = bannerId;
        this.regionIndex = regionIndex;
        this.secuencia = secuencia;
        this.autor = autor;
        this.tipoPincel = tipoPincel;
        this.colorHex = colorHex;
        this.grosor = grosor;
        this.opacidad = opacidad;
        this.puntos = puntos;
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

    public long getSecuencia() {
        return secuencia;
    }

    public UsuarioProyeccion getAutor() {
        return autor;
    }

    public TipoPincel getTipoPincel() {
        return tipoPincel;
    }

    public String getColorHex() {
        return colorHex;
    }

    public double getGrosor() {
        return grosor;
    }

    public double getOpacidad() {
        return opacidad;
    }

    public List<Punto> getPuntos() {
        return puntos;
    }
}
