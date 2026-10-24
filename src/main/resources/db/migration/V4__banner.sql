-- V4__banner.sql
-- Banner comunitario (HU-16): mural 1000x1000 particionado en regiones,
-- consolidado a snapshots PNG periódicamente para no tener que reproducir
-- todo el historial de trazos al cargarlo.

CREATE TABLE banners_comunitarios (
    id              UUID PRIMARY KEY,
    comunidad_id    UUID NOT NULL UNIQUE,
    tamano          INT NOT NULL DEFAULT 1000,
    tamano_region   INT NOT NULL DEFAULT 100
);

CREATE TABLE banner_regiones (
    id                  UUID PRIMARY KEY,
    banner_id           UUID NOT NULL REFERENCES banners_comunitarios(id) ON DELETE CASCADE,
    region_index        INT NOT NULL,
    contador_secuencia  BIGINT NOT NULL DEFAULT 0,
    UNIQUE (banner_id, region_index)
);

CREATE TABLE banner_snapshots (
    id                          UUID PRIMARY KEY,
    banner_id                   UUID NOT NULL REFERENCES banners_comunitarios(id) ON DELETE CASCADE,
    region_index                INT NOT NULL,
    png                         BYTEA NOT NULL,
    ultima_secuencia_incluida   BIGINT NOT NULL,
    actualizado_en              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (banner_id, region_index)
);

CREATE TABLE banner_trazos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    banner_id       UUID NOT NULL REFERENCES banners_comunitarios(id) ON DELETE CASCADE,
    region_index    INT NOT NULL,
    secuencia       BIGINT NOT NULL,
    autor_id        UUID NOT NULL REFERENCES usuarios_proyeccion(usuario_id),
    tipo_pincel     VARCHAR(20) NOT NULL,
    color_hex       VARCHAR(9) NOT NULL,
    grosor          DOUBLE PRECISION NOT NULL,
    opacidad        DOUBLE PRECISION NOT NULL,
    puntos          TEXT NOT NULL,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_banner_trazos_banner_region_secuencia
    ON banner_trazos(banner_id, region_index, secuencia);
