package com.artagram.canvas.kpi;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica hacia artagram-users (dueño de los KPIs de negocio, HU-37/38) el
 * inicio y fin de una sesion colaborativa. Canvas no calcula ningun KPI aqui
 * ni guarda este dato localmente — solo lo reporta, igual que el resto de
 * eventos de dominio que cruzan servicios.
 */
@Component
public class KpiEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KpiEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public KpiEventPublisher(RabbitTemplate rabbitTemplate,
                              @Value("${app.rabbitmq.exchange:artagram.events}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    public void publicarSesionIniciada(UUID usuarioId) {
        Map<String, Object> payload = Map.of(
                "usuarioId", usuarioId.toString(),
                "ocurridoEn", Instant.now().toString()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, "collab.session.started", payload);
        } catch (Exception e) {
            log.warn("No se pudo publicar collab.session.started para usuarioId={}: {}", usuarioId, e.getMessage());
        }
    }

    public void publicarSesionTerminada(UUID usuarioId, long duracionMs) {
        Map<String, Object> payload = Map.of(
                "usuarioId", usuarioId.toString(),
                "duracionMs", duracionMs,
                "ocurridoEn", Instant.now().toString()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, "collab.session.ended", payload);
        } catch (Exception e) {
            log.warn("No se pudo publicar collab.session.ended para usuarioId={}: {}", usuarioId, e.getMessage());
        }
    }
}
