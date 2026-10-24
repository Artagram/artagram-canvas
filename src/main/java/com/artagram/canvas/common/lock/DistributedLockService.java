package com.artagram.canvas.common.lock;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bloqueo distribuido generico por compare-and-set sobre una llave de Redis,
 * usado por el trazo en edicion y el borrador del modo fiesta. Es
 * deliberadamente mas simple que RLock de Redisson: el dueno de un bloqueo se
 * identifica por un valor explicito (usuarioId+sesion), no por hilo/proceso,
 * porque adquirir/renovar/liberar aqui pasan por peticiones HTTP o mensajes
 * STOMP distintos que no comparten el mismo hilo.
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    private static final String SCRIPT_RENOVAR =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('pexpire', KEYS[1], ARGV[2]) " +
            "else return 0 end";

    private static final String SCRIPT_LIBERAR =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    private final RedissonClient redissonClient;

    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /** SET NX PX atomico: solo uno gana si varios lo intentan al mismo tiempo. */
    public boolean adquirir(String clave, String propietario, Duration ttl) {
        boolean adquirido = redissonClient.<String>getBucket(clave, StringCodec.INSTANCE)
                .trySet(propietario, ttl.toMillis(), TimeUnit.MILLISECONDS);
        if (adquirido) {
            log.debug("Lock adquirido: clave={} propietario={} ttlMs={}", clave, propietario, ttl.toMillis());
        } else {
            log.debug("Lock ya estaba tomado: clave={} propietario={}", clave, propietario);
        }
        return adquirido;
    }

    public boolean renovar(String clave, String propietario, Duration ttl) {
        boolean renovado = ejecutar(SCRIPT_RENOVAR, clave, propietario, ttl);
        if (!renovado) {
            log.warn("No se pudo renovar el lock (ya no es dueño o expiró): clave={} propietario={}", clave, propietario);
        }
        return renovado;
    }

    public boolean liberar(String clave, String propietario) {
        boolean liberado = ejecutar(SCRIPT_LIBERAR, clave, propietario, null);
        log.debug("Liberar lock: clave={} propietario={} liberado={}", clave, propietario, liberado);
        return liberado;
    }

    public Optional<String> propietarioActual(String clave) {
        return Optional.ofNullable(redissonClient.<String>getBucket(clave, StringCodec.INSTANCE).get());
    }

    private boolean ejecutar(String script, String clave, String propietario, Duration ttl) {
        RScript rScript = redissonClient.getScript(StringCodec.INSTANCE);
        Long resultado = rScript.eval(RScript.Mode.READ_WRITE, script, RScript.ReturnType.INTEGER,
                Collections.singletonList(clave), propietario, ttl == null ? "" : String.valueOf(ttl.toMillis()));
        return resultado != null && resultado == 1L;
    }
}
