package com.artagram.canvas.party;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * El servidor es el unico que sabe que hora es: el inicio y la duracion de la
 * ronda viven en Redis (memoria distribuida) para que, sin importar cual
 * instancia atienda a cada cliente, todos calculen el mismo tiempo restante a
 * partir del mismo dato.
 */
@Component
public class PartyRoundClock {

    private static final String PREFIJO = "fiesta-reloj:";
    private static final String CLAVE_SALAS_ACTIVAS = "fiesta-salas-activas";

    private final RedissonClient redissonClient;

    public PartyRoundClock(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public void iniciarRonda(UUID salaId, long duracionMs) {
        RMap<String, String> estado = mapa(salaId);
        estado.put("inicioEpochMs", String.valueOf(Instant.now().toEpochMilli()));
        estado.put("duracionMs", String.valueOf(duracionMs));
        estado.put("ultimaVentanaBorradorOfrecida", "-1");
        estado.expire(Duration.ofMillis(duracionMs + 60_000));
        salasActivas().add(salaId.toString());
    }

    public void terminarRonda(UUID salaId) {
        salasActivas().remove(salaId.toString());
    }

    public Set<String> salasActivasIds() {
        return salasActivas().readAll();
    }

    public long msRestantes(UUID salaId) {
        RMap<String, String> estado = mapa(salaId);
        String inicioStr = estado.get("inicioEpochMs");
        String duracionStr = estado.get("duracionMs");
        if (inicioStr == null || duracionStr == null) {
            return 0;
        }
        long fin = Long.parseLong(inicioStr) + Long.parseLong(duracionStr);
        return Math.max(0, fin - Instant.now().toEpochMilli());
    }

    /**
     * Devuelve true la PRIMERA vez que se consulta dentro de una nueva ventana
     * de aparicion del borrador (cada eraserAppearsEverySeconds), para que el
     * llamador ofrezca el borrador una sola vez por ventana.
     */
    public boolean nuevaVentanaDeBorrador(UUID salaId, long ventanaMs) {
        RMap<String, String> estado = mapa(salaId);
        String inicioStr = estado.get("inicioEpochMs");
        if (inicioStr == null) {
            return false;
        }
        long transcurrido = Instant.now().toEpochMilli() - Long.parseLong(inicioStr);
        long ventanaActual = transcurrido / ventanaMs;
        if (ventanaActual < 1) {
            return false;
        }
        String anteriorStr = estado.get("ultimaVentanaBorradorOfrecida");
        long anterior = anteriorStr == null ? -1 : Long.parseLong(anteriorStr);
        if (ventanaActual > anterior) {
            estado.put("ultimaVentanaBorradorOfrecida", String.valueOf(ventanaActual));
            return true;
        }
        return false;
    }

    private RMap<String, String> mapa(UUID salaId) {
        return redissonClient.getMap(PREFIJO + salaId);
    }

    private RSet<String> salasActivas() {
        return redissonClient.getSet(CLAVE_SALAS_ACTIVAS);
    }
}
