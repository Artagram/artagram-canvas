-- V2__canvas.sql
-- Lienzo colaborativo por comunidad (núcleo: dibujo + snapshot + bloqueo de
-- trazos). El banner comunitario y sus migraciones quedan para un PR
-- posterior (ver com.artagram.canvas.canvas/package-info.java si se agrega).

CREATE TABLE lienzos (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comunidad_id        UUID NOT NULL UNIQUE,
    contador_secuencia  BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE trazos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lienzo_id       UUID NOT NULL REFERENCES lienzos(id) ON DELETE CASCADE,
    autor_id        UUID NOT NULL REFERENCES usuarios_proyeccion(usuario_id),
    secuencia       BIGINT NOT NULL,
    tipo_pincel     VARCHAR(20) NOT NULL,
    color_hex       VARCHAR(9) NOT NULL,
    grosor          DOUBLE PRECISION NOT NULL,
    opacidad        DOUBLE PRECISION NOT NULL,
    capa            INT NOT NULL DEFAULT 0,
    puntos          TEXT NOT NULL,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (lienzo_id, secuencia)
);

CREATE INDEX idx_trazos_lienzo_secuencia ON trazos(lienzo_id, secuencia);
