package com.artagram.canvas.common.lock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Metricas de contencion: cuantos bloqueos se negaron y cuanto se espero en
 * promedio por uno (bloqueos_negados_total), y aparte, borrador_disputas_total
 * para el caso especifico del modo fiesta.
 */
@Component
public class ConcurrencyMetrics {

    private final Counter bloqueosNegados;
    private final Timer esperaBloqueo;
    private final Counter borradorDisputas;

    public ConcurrencyMetrics(MeterRegistry registry) {
        this.bloqueosNegados = Counter.builder("bloqueos_negados_total")
                .description("Intentos de tomar un bloqueo distribuido que fueron rechazados")
                .register(registry);
        this.esperaBloqueo = Timer.builder("bloqueo_espera_ms")
                .description("Tiempo que tomo resolver un intento de bloqueo (adquirido o negado)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.borradorDisputas = Counter.builder("borrador_disputas_total")
                .description("Veces que aparecio el borrador del modo fiesta y hubo mas de un participante compitiendo por el")
                .register(registry);
    }

    public void registrarBloqueoNegado() {
        bloqueosNegados.increment();
    }

    public void registrarEsperaBloqueo(long millis) {
        esperaBloqueo.record(Duration.ofMillis(millis));
    }

    public void registrarDisputaBorrador() {
        borradorDisputas.increment();
    }
}
