package com.artagram.canvas.canvas;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Metricas tecnicas relacionadas con el dibujo: latencia de procesamiento de
 * cada trazo y cuantos se estan procesando (trazos_por_segundo se calcula en
 * Prometheus con rate() sobre este contador).
 */
@Component
public class CanvasMetrics {

    private final Timer latenciaTrazo;
    private final Counter trazosFinalizados;

    public CanvasMetrics(MeterRegistry registry) {
        this.latenciaTrazo = Timer.builder("trazo_latencia_ms")
                .description("Tiempo de procesamiento server-side de cada trazo finalizado")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.trazosFinalizados = Counter.builder("trazos_por_segundo")
                .description("Trazos finalizados procesados; usar rate() en Prometheus para la tasa por segundo")
                .register(registry);
    }

    public void registrarLatenciaMs(long millis) {
        latenciaTrazo.record(Duration.ofMillis(millis));
    }

    public void incrementarTrazoFinalizado() {
        trazosFinalizados.increment();
    }
}
