package com.artagram.canvas.banner;

import com.artagram.canvas.banner.dto.BannerTrazoEventoDto;
import com.artagram.canvas.banner.dto.PintarRegionRequest;
import com.artagram.canvas.banner.dto.RegionEstadoDto;
import com.artagram.canvas.canvas.CanvasProperties;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.common.lock.ConcurrencyMetrics;
import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.community.dto.ComunidadResponse;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pintar el banner comunitario particionado en regiones (HU-16). Cada region
 * se serializa con un bloqueo pesimista de fila sobre su contador (igual
 * patron que el lienzo, HU-12); regiones distintas nunca se esperan entre si
 * porque son filas distintas.
 * <p>
 * A diferencia del monolito (que resolvía rol global y autor contra su propio
 * {@code UsuarioRepository}), aquí el rol global llega como parámetro desde
 * el JWT del llamador (igual patrón que {@code ComunidadService.cambiarRol}),
 * y el autor se resuelve vía {@code UsuarioProyeccionResolver}, igual que
 * {@code CanvasService.finalizarTrazo}.
 */
@Service
public class BannerService {

    private static final Logger log = LoggerFactory.getLogger(BannerService.class);

    private final BannerComunitarioRepository bannerRepository;
    private final BannerRegionEstadoRepository regionEstadoRepository;
    private final BannerTrazoRepository bannerTrazoRepository;
    private final BannerSnapshotRepository snapshotRepository;
    private final UsuarioProyeccionResolver usuarioResolver;
    private final ComunidadService comunidadService;
    private final BannerRateLimiter rateLimiter;
    private final ConcurrencyMetrics metrics;
    private final CanvasProperties properties;

    public BannerService(BannerComunitarioRepository bannerRepository, BannerRegionEstadoRepository regionEstadoRepository,
                          BannerTrazoRepository bannerTrazoRepository, BannerSnapshotRepository snapshotRepository,
                          UsuarioProyeccionResolver usuarioResolver, ComunidadService comunidadService,
                          BannerRateLimiter rateLimiter, ConcurrencyMetrics metrics, CanvasProperties properties) {
        this.bannerRepository = bannerRepository;
        this.regionEstadoRepository = regionEstadoRepository;
        this.bannerTrazoRepository = bannerTrazoRepository;
        this.snapshotRepository = snapshotRepository;
        this.usuarioResolver = usuarioResolver;
        this.comunidadService = comunidadService;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.properties = properties;
    }

    /**
     * {@code rolGlobalUsuario} puede venir {@code null} (llamado desde STOMP):
     * es seguro porque {@code BannerTopicAuthorizer} ya exige membresía activa
     * para poder mandar al destino {@code /app/banner/{id}/pintar} en primer
     * lugar, así que la rama de "no soy miembro todavía" nunca se alcanza por
     * ese camino — solo importa de verdad para el POST REST equivalente.
     */
    @Transactional
    public BannerTrazoEventoDto pintar(UUID comunidadId, UUID usuarioId, String nombreUsuario,
                                        String rolGlobalUsuario, PintarRegionRequest request) {
        if (!comunidadService.esMiembroActivo(usuarioId, comunidadId)) {
            ComunidadResponse com = comunidadService.ver(comunidadId, usuarioId);
            boolean esGlobalAdmin = "ADMIN".equalsIgnoreCase(rolGlobalUsuario);
            if (com.publica()) {
                comunidadService.unirse(usuarioId, nombreUsuario, comunidadId);
            } else if (!esGlobalAdmin) {
                throw ApiException.forbidden("NO_ERES_MIEMBRO", "No perteneces a esta comunidad");
            }
        }
        BannerComunitario banner = asegurarBanner(comunidadId);

        int maxRegion = properties.regionsPerSide() * properties.regionsPerSide();
        if (request.regionIndex() >= maxRegion) {
            throw ApiException.badRequest("REGION_INVALIDA", "Esa region no existe");
        }
        if (!rateLimiter.permitir(comunidadId, usuarioId)) {
            metrics.registrarBloqueoNegado();
            throw ApiException.tooManyRequests("LIMITE_DE_RITMO",
                    "Vas muy rapido pintando el banner, baja el ritmo (maximo "
                            + properties.getBannerRateLimitPerSecond() + " operaciones por segundo)");
        }

        BannerRegionEstado region = regionEstadoRepository.buscarParaActualizar(banner.getId(), request.regionIndex())
                .orElseGet(() -> regionEstadoRepository.save(new BannerRegionEstado(banner.getId(), request.regionIndex())));
        long secuencia = region.siguienteSecuencia();

        UsuarioProyeccion autor = usuarioResolver.resolver(usuarioId, nombreUsuario);
        BannerTrazo trazo = new BannerTrazo(banner.getId(), request.regionIndex(), secuencia, autor,
                request.tipoPincel(), request.colorHex(), request.grosor(), request.opacidad(), request.puntos());
        bannerTrazoRepository.save(trazo);
        log.debug("Banner pintado: comunidad={} region={} secuencia={} autor={}",
                comunidadId, request.regionIndex(), secuencia, usuarioId);

        return new BannerTrazoEventoDto(request.regionIndex(), secuencia, usuarioId, nombreUsuario,
                request.tipoPincel(), request.colorHex(), request.grosor(), request.opacidad(), request.puntos());
    }

    @Transactional
    public List<RegionEstadoDto> obtenerEstado(UUID comunidadId) {
        BannerComunitario banner = asegurarBanner(comunidadId);
        int totalRegiones = properties.regionsPerSide() * properties.regionsPerSide();

        return java.util.stream.IntStream.range(0, totalRegiones).mapToObj(regionIndex -> {
            Optional<BannerSnapshot> snapshot = snapshotRepository.findByBannerIdAndRegionIndex(banner.getId(), regionIndex);
            long desde = snapshot.map(BannerSnapshot::getUltimaSecuenciaIncluida).orElse(0L);
            List<BannerTrazoEventoDto> pendientes = bannerTrazoRepository
                    .findByBannerIdAndRegionIndexAndSecuenciaGreaterThanOrderBySecuenciaAsc(banner.getId(), regionIndex, desde)
                    .stream()
                    .map(t -> new BannerTrazoEventoDto(t.getRegionIndex(), t.getSecuencia(), t.getAutor().getUsuarioId(),
                            t.getAutor().getNombreArtista(), t.getTipoPincel(), t.getColorHex(), t.getGrosor(),
                            t.getOpacidad(), t.getPuntos()))
                    .toList();
            String base64 = snapshot.map(s -> Base64.getEncoder().encodeToString(s.getPng())).orElse(null);
            return new RegionEstadoDto(regionIndex, base64, pendientes);
        }).toList();
    }

    private BannerComunitario asegurarBanner(UUID comunidadId) {
        return bannerRepository.findByComunidadId(comunidadId).orElseGet(() -> {
            BannerComunitario b = bannerRepository.save(
                    new BannerComunitario(comunidadId, properties.getBannerSize(), properties.getBannerRegionSize()));
            int totalRegiones = properties.regionsPerSide() * properties.regionsPerSide();
            for (int i = 0; i < totalRegiones; i++) {
                regionEstadoRepository.save(new BannerRegionEstado(b.getId(), i));
            }
            log.info("Banner auto-aprovisionado para comunidad {}", comunidadId);
            return b;
        });
    }
}
