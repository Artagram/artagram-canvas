-- V3__community.sql
-- Comunidades y membresías, migradas desde ARTAGRAM-backv1. El banner
-- comunitario (y sus tablas) sigue pendiente para un PR posterior.

CREATE TABLE comunidades (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre          VARCHAR(80) NOT NULL UNIQUE,
    descripcion     VARCHAR(500) NOT NULL DEFAULT '',
    publica         BOOLEAN NOT NULL DEFAULT TRUE,
    creador_id      UUID NOT NULL REFERENCES usuarios_proyeccion(usuario_id),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE membresias_comunidad (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comunidad_id    UUID NOT NULL REFERENCES comunidades(id) ON DELETE CASCADE,
    usuario_id      UUID NOT NULL REFERENCES usuarios_proyeccion(usuario_id),
    rol             VARCHAR(20) NOT NULL,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (comunidad_id, usuario_id)
);

CREATE INDEX idx_membresias_usuario ON membresias_comunidad(usuario_id);
CREATE INDEX idx_membresias_comunidad ON membresias_comunidad(comunidad_id);
