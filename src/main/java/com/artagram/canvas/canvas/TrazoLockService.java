package com.artagram.canvas.canvas;

import com.artagram.canvas.canvas.dto.BloqueoEventoDto;
import com.artagram.canvas.canvas.dto.BloqueoTrazoResponse;
import com.artagram.canvas.canvas.dto.EditarTrazoRequest;
import com.artagram.canvas.canvas.dto.TrazoEventoDto;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.common.lock.ConcurrencyMetrics;
import com.artagram.canvas.common.lock.DistributedLockService;
import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueo por trazo: mientras alguien lo tiene tomado, nadie mas puede
 * editarlo. TTL de 30s con renovacion por heartbeat; si el dueno se cae o no
 * manda heartbeat, el bloqueo se suelta solo cuando vence la llave en Redis.
 */
@Service
public class TrazoLockService {

    private static final Logger log = LoggerFactory.getLogger(TrazoLockService.class);
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String SEPARADOR = "::";

    private final DistributedLockService lockService;
    private final TrazoRepository trazoRepository;
    private final ComunidadService comunidadService;
    private final RealtimeBroadcaster broadcaster;
    private final ConcurrencyMetrics metrics;

    public TrazoLockService(DistributedLockService lockService, TrazoRepository trazoRepository,
                             ComunidadService comunidadService,
                             RealtimeBroadcaster broadcaster, ConcurrencyMetrics metrics) {
        this.lockService = lockService;
        this.trazoRepository = trazoRepository;
        this.comunidadService = comunidadService;
        this.broadcaster = broadcaster;
        this.metrics = metrics;
    }

    public BloqueoTrazoResponse tomar(UUID comunidadId, UUID trazoId, UUID usuarioId, String nombreUsuario) {
        exigirMiembro(usuarioId, comunidadId);
        String clave = clave(trazoId);
        String propietario = propietario(usuarioId, nombreUsuario);
        long inicio = System.nanoTime();
        // Reentrante para el mismo dueno (p. ej. recargo la pagina con el trazo tomado):
        // se renueva en vez de rechazarlo hasta que venza el TTL.
        boolean adquirido = lockService.adquirir(clave, propietario, TTL)
                || (lockService.propietarioActual(clave).filter(propietario::equals).isPresent()
                        && lockService.renovar(clave, propietario, TTL));
        metrics.registrarEsperaBloqueo((System.nanoTime() - inicio) / 1_000_000);

        if (adquirido) {
            log.debug("Trazo bloqueado: trazo={} propietario={}", trazoId, propietario);
            broadcaster.enviar("/topic/lienzo/" + comunidadId + "/bloqueos",
                    new BloqueoEventoDto("TOMADO", trazoId, nombreUsuario));
            return new BloqueoTrazoResponse(true, nombreUsuario);
        }
        metrics.registrarBloqueoNegado();
        String actual = lockService.propietarioActual(clave).map(this::extraerNombre).orElse("alguien");
        log.debug("Bloqueo de trazo denegado: trazo={} solicitante={} dueñoActual={}", trazoId, nombreUsuario, actual);
        return new BloqueoTrazoResponse(false, actual);
    }

    public boolean renovar(UUID trazoId, UUID usuarioId, String nombreUsuario) {
        return lockService.renovar(clave(trazoId), propietario(usuarioId, nombreUsuario), TTL);
    }

    public void liberar(UUID comunidadId, UUID trazoId, UUID usuarioId, String nombreUsuario) {
        if (lockService.liberar(clave(trazoId), propietario(usuarioId, nombreUsuario))) {
            broadcaster.enviar("/topic/lienzo/" + comunidadId + "/bloqueos",
                    new BloqueoEventoDto("LIBERADO", trazoId, null));
        }
    }

    @Transactional
    public void editar(UUID comunidadId, UUID trazoId, UUID usuarioId, String nombreUsuario, EditarTrazoRequest request) {
        exigirMiembro(usuarioId, comunidadId);
        String clave = clave(trazoId);
        String propietario = propietario(usuarioId, nombreUsuario);
        String actual = lockService.propietarioActual(clave).orElse(null);
        if (actual == null || !actual.equals(propietario)) {
            metrics.registrarBloqueoNegado();
            log.warn("Intento de editar trazo sin tenerlo bloqueado: trazo={} solicitante={} dueñoActual={}",
                    trazoId, nombreUsuario, actual);
            throw ApiException.forbidden("TRAZO_NO_TOMADO",
                    "No tienes tomado este trazo" + (actual != null ? " (lo tiene " + extraerNombre(actual) + ")" : ""));
        }
        Trazo trazo = trazoRepository.findById(trazoId)
                .orElseThrow(() -> ApiException.notFound("TRAZO_NO_ENCONTRADO", "Ese trazo no existe"));
        trazo.actualizarContenido(request.tipoPincel(), request.colorHex(), request.grosor(),
                request.opacidad(), request.capa(), request.puntos());

        broadcaster.enviar("/topic/lienzo/" + comunidadId, aEventoEditado(trazo));
    }

    private TrazoEventoDto aEventoEditado(Trazo trazo) {
        return new TrazoEventoDto("EDITADO", null, trazo.getSecuencia(),
                trazo.getAutor().getUsuarioId(), trazo.getAutor().getNombreArtista(), trazo.getTipoPincel(),
                trazo.getColorHex(), trazo.getGrosor(), trazo.getOpacidad(), trazo.getCapa(), trazo.getPuntos(),
                trazo.getId());
    }

    private void exigirMiembro(UUID usuarioId, UUID comunidadId) {
        if (!comunidadService.esMiembroActivo(usuarioId, comunidadId)) {
            throw ApiException.forbidden("NO_ERES_MIEMBRO", "No perteneces a esta comunidad");
        }
    }

    private String clave(UUID trazoId) {
        return "trazo-lock:" + trazoId;
    }

    private String propietario(UUID usuarioId, String nombreUsuario) {
        return usuarioId + SEPARADOR + nombreUsuario;
    }

    private String extraerNombre(String propietario) {
        int idx = propietario.indexOf(SEPARADOR);
        return idx >= 0 ? propietario.substring(idx + SEPARADOR.length()) : propietario;
    }
}
