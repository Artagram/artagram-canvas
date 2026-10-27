-- V5__banner_snapshot_s3.sql
-- El PNG sigue persistiendo en Postgres (BannerSnapshotJob lo necesita como
-- base para el renderizado incremental de la siguiente consolidacion), pero
-- ahora tambien se sube a S3 bajo esta clave: el cliente lo carga desde ahi
-- (URL presignada, peticion HTTP cacheable e independiente por region) en
-- vez de recibir un blob base64 embebido en el JSON de /banner, que crecia
-- con cada region del mural.

ALTER TABLE banner_snapshots ADD COLUMN s3_key VARCHAR(255);
