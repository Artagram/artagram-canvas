package com.artagram.canvas.banner;

import com.artagram.canvas.canvas.CanvasProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limite de 100 operaciones por segundo por usuario sobre el banner (HU-16,
 * escenario 7): si alguien pinta con un script, el servidor bota lo que sobra
 * en vez de dejar que un solo cliente ahogue a los demas. El contador vive en
 * Redis, asi que el limite se respeta aunque el usuario cambie de replica.
 * <p>
 * Cada exceso suma a {@code banner_eventos_descartados_total}. En el log, el
 * primer exceso de un usuario sale de inmediato (deteccion en menos de 1 s) y
 * los siguientes se resumen una vez por segundo con su cantidad, para que un
 * ataque de 1000 eventos por segundo no se convierta en 900 lineas por segundo.
 */
@Component
public class BannerRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(BannerRateLimiter.class);
    private static final long INTERVALO_LOG_MS = 1000;

    private final RedissonClient redissonClient;
    private final CanvasProperties properties;
    private final Counter descartados;
    private final LongSupplier reloj;
    private final Map<String, Excesos> excesos = new ConcurrentHashMap<>();

    private static final class Excesos {
        final AtomicLong pendientes = new AtomicLong();
        volatile long ultimoLogMs;
    }

    @Autowired
    public BannerRateLimiter(RedissonClient redissonClient, CanvasProperties properties, MeterRegistry registry) {
        this(redissonClient, properties, registry, System::currentTimeMillis);
    }

    BannerRateLimiter(RedissonClient redissonClient, CanvasProperties properties, MeterRegistry registry,
                      LongSupplier reloj) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.reloj = reloj;
        this.descartados = Counter.builder("banner_eventos_descartados_total")
                .description("Eventos de banner descartados por superar el limite por usuario")
                .register(registry);
    }

    public boolean permitir(UUID comunidadId, UUID usuarioId) {
        try {
            RRateLimiter limiter = redissonClient.getRateLimiter("banner-rate:" + comunidadId + ":" + usuarioId);
            limiter.trySetRate(RateType.OVERALL, properties.getBannerRateLimitPerSecond(), 1, RateIntervalUnit.SECONDS);
            boolean permitido = limiter.tryAcquire();
            if (!permitido) {
                registrarExceso(comunidadId, usuarioId);
            }
            return permitido;
        } catch (Exception e) {
            log.warn("No se pudo contactar Redis para rate limiter de banner ({}), permitiendo por degradacion suave", e.getMessage());
            return true;
        }
    }

    private void registrarExceso(UUID comunidadId, UUID usuarioId) {
        descartados.increment();
        Excesos registro = excesos.computeIfAbsent(comunidadId + ":" + usuarioId, k -> new Excesos());
        registro.pendientes.incrementAndGet();
        long ahora = reloj.getAsLong();
        if (ahora - registro.ultimoLogMs >= INTERVALO_LOG_MS) {
            registro.ultimoLogMs = ahora;
            long enEsteIntervalo = registro.pendientes.getAndSet(0);
            log.warn("Limite de ritmo excedido en banner: comunidad={} usuario={} descartados={}", // NOSONAR javasecurity:S5145 UUID tipado, sin saltos de línea
                    comunidadId, usuarioId, enEsteIntervalo);
        }
    }
}
