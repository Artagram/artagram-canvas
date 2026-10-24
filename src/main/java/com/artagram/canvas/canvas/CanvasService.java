package com.artagram.canvas.canvas;

import com.artagram.canvas.canvas.dto.EnviarPuntosRequest;
import com.artagram.canvas.canvas.dto.TrazoEventoDto;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanvasService {

    private static final Logger log = LoggerFactory.getLogger(CanvasService.class);

    private final Map<String, Long> cacheMiembros = new ConcurrentHashMap<>();

    private final LienzoRepository lienzoRepository;
    private final TrazoRepository trazoRepository;
    private final UsuarioProyeccionResolver usuarioResolver;
    private final ComunidadService comunidadService;
    private final CanvasMetrics metrics;

    public CanvasService(LienzoRepository lienzoRepository, TrazoRepository trazoRepository,
                          UsuarioProyeccionResolver usuarioResolver, ComunidadService comunidadService,
                          CanvasMetrics metrics) {
        this.lienzoRepository = lienzoRepository;
        this.trazoRepository = trazoRepository;
        this.usuarioResolver = usuarioResolver;
        this.comunidadService = comunidadService;
        this.metrics = metrics;
    }

    /**
     * Solo relay: no se persiste nada mientras el trazo esta en progreso, para
     * que dibujar se sienta instantaneo y el lienzo no crezca con basura si
     * alguien nunca suelta el pincel.
     */
    public TrazoEventoDto procesarParcial(UUID comunidadId, UUID autorId, String autorNombre, EnviarPuntosRequest request) {
        exigirMiembroActivo(autorId, comunidadId);
        return TrazoEventoDto.parcial(request.trazoEnProgresoId(), autorId, autorNombre, request);
    }

    @Transactional
    public TrazoEventoDto finalizarTrazo(UUID comunidadId, UUID autorId, String autorNombre, EnviarPuntosRequest request) {
        exigirMiembroActivo(autorId, comunidadId);
        Lienzo lienzo = obtenerOCrearLienzo(comunidadId);
        long secuencia = lienzo.siguienteSecuencia();

        UsuarioProyeccion autor = usuarioResolver.resolver(autorId, autorNombre);
        Trazo trazo = new Trazo(lienzo.getId(), autor, secuencia, request.tipoPincel(), request.colorHex(),
                request.grosor(), request.opacidad(), request.capa(), request.puntos());
        trazoRepository.save(trazo);
        metrics.incrementarTrazoFinalizado();
        log.debug("Trazo finalizado: comunidad={} secuencia={} autor={}", comunidadId, secuencia, autorId);

        return TrazoEventoDto.finalizado(request.trazoEnProgresoId(), trazo.getId(), secuencia, autorId, autorNombre, request);
    }

    public void registrarLatencia(long millis) {
        metrics.registrarLatenciaMs(millis);
    }

    @Transactional(readOnly = true)
    public List<Trazo> obtenerTrazosDesde(UUID comunidadId, long desdeSecuencia) {
        return lienzoRepository.findByComunidadId(comunidadId)
                .map(lienzo -> trazoRepository.findByLienzoIdAndSecuenciaGreaterThanOrderBySecuenciaAsc(lienzo.getId(), desdeSecuencia))
                .orElseGet(List::of);
    }

    /**
     * A diferencia del monolito (donde {@code CanvasProvisioningService}
     * escuchaba un evento in-process al crear la comunidad), aquí el lienzo
     * se crea perezosamente en su primer trazo. Ahora que {@code community}
     * también vive en este mismo servicio, se podría volver al provisioning
     * por evento — se deja así por ahora porque es igual de correcto y más
     * simple (una responsabilidad menos que mantener sincronizada).
     */
    private Lienzo obtenerOCrearLienzo(UUID comunidadId) {
        return lienzoRepository.buscarParaActualizar(comunidadId)
                .orElseGet(() -> lienzoRepository.save(new Lienzo(comunidadId)));
    }

    private void exigirMiembroActivo(UUID usuarioId, UUID comunidadId) {
        String clave = usuarioId + ":" + comunidadId;
        Long expira = cacheMiembros.get(clave);
        long ahora = System.currentTimeMillis();
        if (expira != null && ahora < expira) {
            return;
        }
        if (!comunidadService.esMiembroActivo(usuarioId, comunidadId)) {
            cacheMiembros.remove(clave);
            throw ApiException.forbidden("NO_ERES_MIEMBRO", "No perteneces a esta comunidad");
        }
        cacheMiembros.put(clave, ahora + 30_000L);
    }
}
