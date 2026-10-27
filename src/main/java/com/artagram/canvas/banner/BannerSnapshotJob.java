package com.artagram.canvas.banner;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cada cierto tiempo aplana los trazos nuevos de cada region sobre su ultimo
 * snapshot (HU-16, nota tecnica): asi cargar el banner no implica reproducir
 * todo el historial. Solo consolida regiones con trazos nuevos de sobra.
 * Reusa el {@code @EnableScheduling} que ya declara {@code CanvasApplication}
 * para el reloj de rondas del modo fiesta.
 */
@Component
public class BannerSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(BannerSnapshotJob.class);
    private static final long UMBRAL_TRAZOS_PENDIENTES = 20;

    private final BannerComunitarioRepository bannerRepository;
    private final BannerRegionEstadoRepository regionRepository;
    private final BannerTrazoRepository trazoRepository;
    private final BannerSnapshotRepository snapshotRepository;
    private final BannerRasterService rasterService;
    private final BannerSnapshotStorageService storageService;

    public BannerSnapshotJob(BannerComunitarioRepository bannerRepository, BannerRegionEstadoRepository regionRepository,
                              BannerTrazoRepository trazoRepository, BannerSnapshotRepository snapshotRepository,
                              BannerRasterService rasterService, BannerSnapshotStorageService storageService) {
        this.bannerRepository = bannerRepository;
        this.regionRepository = regionRepository;
        this.trazoRepository = trazoRepository;
        this.snapshotRepository = snapshotRepository;
        this.rasterService = rasterService;
        this.storageService = storageService;
    }

    @Scheduled(fixedDelayString = "${app.canvas.snapshot-interval-ms:30000}")
    @Transactional
    public void consolidar() {
        for (BannerComunitario banner : bannerRepository.findAll()) {
            for (BannerRegionEstado region : regionRepository.findByBannerId(banner.getId())) {
                consolidarRegion(banner, region);
            }
        }
    }

    private void consolidarRegion(BannerComunitario banner, BannerRegionEstado region) {
        var snapshotExistente = snapshotRepository.findByBannerIdAndRegionIndex(banner.getId(), region.getRegionIndex());
        long desde = snapshotExistente.map(BannerSnapshot::getUltimaSecuenciaIncluida).orElse(0L);
        long pendientes = trazoRepository.countByBannerIdAndRegionIndexAndSecuenciaGreaterThan(banner.getId(), region.getRegionIndex(), desde);
        if (pendientes < UMBRAL_TRAZOS_PENDIENTES) {
            return;
        }
        List<BannerTrazo> nuevos = trazoRepository
                .findByBannerIdAndRegionIndexAndSecuenciaGreaterThanOrderBySecuenciaAsc(banner.getId(), region.getRegionIndex(), desde);
        byte[] base = snapshotExistente.map(BannerSnapshot::getPng).orElse(null);
        byte[] renderizado = rasterService.renderizar(base, banner.getTamanoRegion(), nuevos);
        long nuevaSecuencia = nuevos.get(nuevos.size() - 1).getSecuencia();

        BannerSnapshot snapshot = snapshotExistente
                .map(s -> {
                    s.actualizar(renderizado, nuevaSecuencia);
                    return s;
                })
                .orElseGet(() -> snapshotRepository.save(
                        new BannerSnapshot(banner.getId(), region.getRegionIndex(), renderizado, nuevaSecuencia)));

        String key = storageService.claveDe(banner.getId(), region.getRegionIndex());
        if (storageService.subir(key, renderizado)) {
            snapshot.setS3Key(key);
        }

        log.info("Snapshot consolidado banner={} region={} hastaSecuencia={}", banner.getId(), region.getRegionIndex(), nuevaSecuencia);
    }
}
