package com.artagram.canvas.party;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El unico reloj autoritativo: cada tickIntervalMs recorre las salas activas
 * (el set vive en Redis, no en memoria de este proceso) y les manda a todos
 * el mismo numero. Tambien decide cuando aparece el borrador y cuando se
 * congela la ronda.
 */
@Component
public class PartyTickScheduler {

    private static final Logger log = LoggerFactory.getLogger(PartyTickScheduler.class);

    private final PartyRoundClock roundClock;
    private final SalaFiestaService salaFiestaService;
    private final PartyProperties properties;

    public PartyTickScheduler(PartyRoundClock roundClock, SalaFiestaService salaFiestaService, PartyProperties properties) {
        this.roundClock = roundClock;
        this.salaFiestaService = salaFiestaService;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "${app.party.tick-interval-ms:500}")
    public void tick() {
        long ventanaMs = properties.getEraserAppearsEverySeconds() * 1000L;
        for (String idStr : roundClock.salasActivasIds()) {
            // Una sala en mal estado no puede tumbar el tick de las demas: cada una
            // se procesa aislada, así una excepción puntual solo le pega a esa sala
            // (se reintenta sola en el siguiente tick) en vez de congelar el reloj
            // de todo el modo fiesta hasta el próximo deploy.
            try {
                procesarSala(UUID.fromString(idStr), ventanaMs);
            } catch (RuntimeException e) {
                log.error("Fallo procesando el tick de la sala {}", idStr, e);
            }
        }
    }

    private void procesarSala(UUID salaId, long ventanaMs) {
        long restante = roundClock.msRestantes(salaId);
        if (restante <= 0) {
            roundClock.terminarRonda(salaId);
            try {
                cerrarRonda(salaId);
            } catch (Exception e) {
                log.warn("Error cerrando ronda de sala {}: {}", salaId, e.getMessage());
            }
            return;
        }
        salaFiestaService.notificarTick(salaId, restante);
        if (roundClock.nuevaVentanaDeBorrador(salaId, ventanaMs)) {
            salaFiestaService.notificarBorradorDisponible(salaId);
        }
    }

    private void cerrarRonda(UUID salaId) {
        salaFiestaService.terminarRonda(salaId);
        salaFiestaService.notificarFinDeRonda(salaId);
    }
}
