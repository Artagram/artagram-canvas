package com.artagram.canvas.banner;

import com.artagram.canvas.canvas.CanvasProperties;
import java.util.UUID;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Limite de 100 operaciones por segundo por usuario sobre el banner (HU-16):
 * si alguien pinta con un script, el servidor bota lo que sobra en vez de
 * dejar que un solo cliente ahogue a los demas.
 */
@Component
public class BannerRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(BannerRateLimiter.class);

    private final RedissonClient redissonClient;
    private final CanvasProperties properties;

    public BannerRateLimiter(RedissonClient redissonClient, CanvasProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    public boolean permitir(UUID comunidadId, UUID usuarioId) {
        try {
            RRateLimiter limiter = redissonClient.getRateLimiter("banner-rate:" + comunidadId + ":" + usuarioId);
            limiter.trySetRate(RateType.OVERALL, properties.getBannerRateLimitPerSecond(), 1, RateIntervalUnit.SECONDS);
            boolean permitido = limiter.tryAcquire();
            if (!permitido) {
                log.warn("Limite de ritmo excedido en banner: comunidad={} usuario={}", comunidadId, usuarioId);
            }
            return permitido;
        } catch (Exception e) {
            log.warn("No se pudo contactar Redis para rate limiter de banner ({}), permitiendo por degradacion suave", e.getMessage());
            return true;
        }
    }
}
