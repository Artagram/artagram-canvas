-- V1__party.sql
-- Primer corte migrado desde ARTAGRAM-backv1: proyección local de usuario +
-- esquema completo del modo fiesta (salas, participantes, rondas y trazos).
-- El lienzo comunitario (canvas/banners) y sus migraciones se agregan en un
-- PR posterior (ver README.md y com.artagram.canvas.canvas/package-info.java).

CREATE TABLE usuarios_proyeccion (
    usuario_id      UUID PRIMARY KEY,
    nombre_artista  VARCHAR(100) NOT NULL,
    premium         BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE salas_fiesta (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo                  VARCHAR(8) NOT NULL UNIQUE,
    anfitrion_id            UUID NOT NULL REFERENCES usuarios_proyeccion(usuario_id),
    duracion_ronda_segundos INT NOT NULL,
    cupo_maximo             INT NOT NULL,
    total_rondas            INT NOT NULL DEFAULT 3,
    estado                  VARCHAR(20) NOT NULL DEFAULT 'ESPERANDO',
    numero_ronda            INT NOT NULL DEFAULT 0,
    pincel_actual           VARCHAR(20),
    color_actual            VARCHAR(9),
    grosor_actual           DOUBLE PRECISION,
    opacidad_actual         DOUBLE PRECISION,
    creado_en               TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE participantes_sala (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sala_id         UUID NOT NULL REFERENCES salas_fiesta(id) ON DELETE CASCADE,
    usuario_id      UUID NOT NULL REFERENCES usuarios_proyeccion(usuario_id),
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (sala_id, usuario_id)
);

CREATE TABLE party_ronda_estado (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sala_id             UUID NOT NULL REFERENCES salas_fiesta(id) ON DELETE CASCADE,
    numero_ronda        INT NOT NULL,
    contador_secuencia  BIGINT NOT NULL DEFAULT 0,
    UNIQUE (sala_id, numero_ronda)
);

CREATE TABLE party_trazos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sala_id         UUID NOT NULL REFERENCES salas_fiesta(id) ON DELETE CASCADE,
    numero_ronda    INT NOT NULL,
    secuencia       BIGINT NOT NULL,
    autor_id        UUID NOT NULL REFERENCES usuarios_proyeccion(usuario_id),
    borrador        BOOLEAN NOT NULL DEFAULT FALSE,
    tipo_pincel     VARCHAR(20),
    color_hex       VARCHAR(9),
    grosor          DOUBLE PRECISION NOT NULL,
    opacidad        DOUBLE PRECISION NOT NULL,
    puntos          TEXT NOT NULL,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (sala_id, numero_ronda, secuencia)
);

CREATE INDEX idx_party_trazos_ronda ON party_trazos(sala_id, numero_ronda, secuencia);
CREATE INDEX idx_participantes_sala_usuario ON participantes_sala(usuario_id);
