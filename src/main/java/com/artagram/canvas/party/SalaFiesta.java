package com.artagram.canvas.party;

import com.artagram.canvas.canvas.TipoPincel;
import com.artagram.canvas.common.domain.BaseEntity;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "salas_fiesta")
public class SalaFiesta extends BaseEntity {

    @Column(nullable = false, unique = true, length = 8)
    private String codigo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "anfitrion_id", nullable = false)
    private UsuarioProyeccion anfitrion;

    @Column(name = "duracion_ronda_segundos", nullable = false)
    private int duracionRondaSegundos;

    @Column(name = "cupo_maximo", nullable = false)
    private int cupoMaximo;

    @Column(name = "total_rondas", nullable = false)
    private int totalRondas = 3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSala estado = EstadoSala.ESPERANDO;

    @Column(name = "numero_ronda", nullable = false)
    private int numeroRonda = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "pincel_actual", length = 20)
    private TipoPincel pincelActual;

    @Column(name = "color_actual", length = 9)
    private String colorActual;

    @Column(name = "grosor_actual")
    private Double grosorActual;

    @Column(name = "opacidad_actual")
    private Double opacidadActual;

    protected SalaFiesta() {
    }

    public SalaFiesta(String codigo, UsuarioProyeccion anfitrion, int duracionRondaSegundos, int cupoMaximo, int totalRondas) {
        this.codigo = codigo;
        this.anfitrion = anfitrion;
        this.duracionRondaSegundos = duracionRondaSegundos;
        this.cupoMaximo = cupoMaximo;
        this.totalRondas = totalRondas > 0 ? totalRondas : 3;
    }

    public String getCodigo() {
        return codigo;
    }

    public UsuarioProyeccion getAnfitrion() {
        return anfitrion;
    }

    public int getDuracionRondaSegundos() {
        return duracionRondaSegundos;
    }

    public int getCupoMaximo() {
        return cupoMaximo;
    }

    public EstadoSala getEstado() {
        return estado;
    }

    public void setEstado(EstadoSala estado) {
        this.estado = estado;
    }

    public int getNumeroRonda() {
        return numeroRonda;
    }

    public TipoPincel getPincelActual() {
        return pincelActual;
    }

    public String getColorActual() {
        return colorActual;
    }

    public Double getGrosorActual() {
        return grosorActual;
    }

    public Double getOpacidadActual() {
        return opacidadActual;
    }

    public void iniciarNuevaRonda(TipoPincel pincel, String color, double grosor, double opacidad) {
        this.numeroRonda++;
        this.estado = EstadoSala.EN_RONDA;
        this.pincelActual = pincel;
        this.colorActual = color;
        this.grosorActual = grosor;
        this.opacidadActual = opacidad;
    }

    public void congelar() {
        this.estado = EstadoSala.ESPERANDO;
    }

    public int getTotalRondas() {
        return totalRondas;
    }
}
