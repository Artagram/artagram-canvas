package com.artagram.canvas.community;

import com.artagram.canvas.common.domain.BaseEntity;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "membresias_comunidad", uniqueConstraints = @UniqueConstraint(columnNames = {"comunidad_id", "usuario_id"}))
public class MembresiaComunidad extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioProyeccion usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolComunidad rol;

    @Column(nullable = false)
    private boolean activo = true;

    protected MembresiaComunidad() {
    }

    public MembresiaComunidad(Comunidad comunidad, UsuarioProyeccion usuario, RolComunidad rol) {
        this.comunidad = comunidad;
        this.usuario = usuario;
        this.rol = rol;
    }

    public Comunidad getComunidad() {
        return comunidad;
    }

    public UsuarioProyeccion getUsuario() {
        return usuario;
    }

    public RolComunidad getRol() {
        return rol;
    }

    public void setRol(RolComunidad rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void expulsar() {
        this.activo = false;
    }

    public void reactivar() {
        this.activo = true;
    }
}
