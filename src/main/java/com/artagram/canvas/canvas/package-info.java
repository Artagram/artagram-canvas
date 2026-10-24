/**
 * Lienzo comunitario: dibujo colaborativo en tiempo real, snapshot REST para
 * quien llega tarde, y bloqueo exclusivo por trazo. Migrado desde
 * {@code ARTAGRAM-backv1/src/main/java/com/artagram/platform/canvas} y
 * probado end-to-end (membresía, dibujar, bloquear/editar/liberar un trazo).
 * <p>
 * La membresía de comunidad se valida contra {@code com.artagram.canvas.community.ComunidadService},
 * que vive en este mismo servicio (ver ese paquete). El lienzo de una
 * comunidad se crea perezosamente en su primer trazo
 * ({@code CanvasService.obtenerOCrearLienzo}) en vez de por evento al crear
 * la comunidad — ambos viven en el mismo proceso, así que esto es solo una
 * elección de simplicidad, no una limitación real.
 * <p>
 * Pendiente para un PR posterior: el banner comunitario
 * ({@code BannerComunitario}, {@code BannerRasterService},
 * {@code BannerRateLimiter}, snapshots PNG) y sus migraciones.
 */
package com.artagram.canvas.canvas;
