/**
 * Banner comunitario (HU-16): mural 1000×1000 particionado en regiones,
 * pintado por REST o STOMP, consolidado a snapshots PNG por
 * {@code BannerSnapshotJob}. Migrado desde
 * {@code ARTAGRAM-backv1/src/main/java/com/artagram/platform/canvas}
 * (clases {@code Banner*}).
 * <p>
 * Dos adaptaciones reales respecto al monolito (no simplificaciones, cambios
 * de fondo forzados por no compartir base de datos con {@code usuarios}):
 * <ul>
 *   <li>El autor de un trazo es {@code UsuarioProyeccion}, no {@code Usuario}
 *   — igual patrón que {@code canvas.Trazo}.</li>
 *   <li>El chequeo de admin-global ya no consulta un {@code UsuarioRepository}:
 *   llega como el claim {@code rolGlobal} del JWT del llamador. Por eso
 *   {@code BannerService.pintar} ahora recibe ese rol como parámetro
 *   (puede ser {@code null} desde STOMP: ver el javadoc del método — es
 *   seguro porque {@code BannerTopicAuthorizer} ya exige membresía o
 *   comunidad pública antes de dejar pasar el SEND).</li>
 * </ul>
 */
package com.artagram.canvas.banner;
