package com.artagram.canvas.canvas;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un trazo terminado: registro append-only, nunca se edita ni se borra desde
 * aqui (la edicion controlada llega via el bloqueo de {@code TrazoLockService}).
 * El numero de secuencia lo asigna el lienzo en la misma transaccion.
 */
@Entity
@Table(name = "trazos", uniqueConstraints = @UniqueConstraint(columnNames = {"lienzo_id", "secuencia"}))
public class Trazo {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "lienzo_id", nullable = false)
    private UUID lienzoId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private UsuarioProyeccion autor;

    @Column(nullable = false)
    private long secuencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pincel", nullable = false, length = 20)
    private TipoPincel tipoPincel;

    @Column(name = "color_hex", nullable = false, length = 9)
    private String colorHex;

    @Column(nullable = false)
    private double grosor;

    @Column(nullable = false)
    private double opacidad;

    @Column(nullable = false)
    private int capa;

    @Convert(converter = PuntosConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<Punto> puntos;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected Trazo() {
    }

    public Trazo(UUID lienzoId, UsuarioProyeccion autor, long secuencia, TipoPincel tipoPincel, String colorHex,
                 double grosor, double opacidad, int capa, List<Punto> puntos) {
        this.lienzoId = lienzoId;
        this.autor = autor;
        this.secuencia = secuencia;
        this.tipoPincel = tipoPincel;
        this.colorHex = colorHex;
        this.grosor = grosor;
        this.opacidad = opacidad;
        this.capa = capa;
        this.puntos = puntos;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLienzoId() {
        return lienzoId;
    }

    public UsuarioProyeccion getAutor() {
        return autor;
    }

    public long getSecuencia() {
        return secuencia;
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

    public int getCapa() {
        return capa;
    }

    public List<Punto> getPuntos() {
        return puntos;
    }

    /** Solo se debe llamar mientras el llamador tiene el bloqueo de este trazo. */
    public void actualizarContenido(TipoPincel tipoPincel, String colorHex, double grosor, double opacidad,
                                     int capa, List<Punto> puntos) {
        this.tipoPincel = tipoPincel;
        this.colorHex = colorHex;
        this.grosor = grosor;
        this.opacidad = opacidad;
        this.capa = capa;
        this.puntos = puntos;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
