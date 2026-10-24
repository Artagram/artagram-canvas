package com.artagram.canvas.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Copia local y minima de un usuario (artagram-canvas no es dueño de datos de
 * usuario). Se mantiene sincronizada vía eventos de RabbitMQ
 * ({@code user.registered}, {@code subscription.updated}, {@code user.deleted})
 * publicados por artagram-auth/artagram-users, tal como lo documenta
 * {@code artagram-infra/docs/PLAN_MIGRACION_ARTAGRAM.md} para la tabla
 * {@code usuarios_proyeccion} de {@code db_lienzo}.
 */
@Entity
@Table(name = "usuarios_proyeccion")
public class UsuarioProyeccion {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "nombre_artista", nullable = false)
    private String nombreArtista;

    @Column(nullable = false)
    private boolean premium;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected UsuarioProyeccion() {
    }

    public UsuarioProyeccion(UUID usuarioId, String nombreArtista, boolean premium) {
        this.usuarioId = usuarioId;
        this.nombreArtista = nombreArtista;
        this.premium = premium;
        this.creadoEn = Instant.now();
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getNombreArtista() {
        return nombreArtista;
    }

    public void setNombreArtista(String nombreArtista) {
        this.nombreArtista = nombreArtista;
    }

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
