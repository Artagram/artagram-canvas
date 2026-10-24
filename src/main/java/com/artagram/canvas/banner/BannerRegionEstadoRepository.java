package com.artagram.canvas.banner;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BannerRegionEstadoRepository extends JpaRepository<BannerRegionEstado, UUID> {

    List<BannerRegionEstado> findByBannerId(UUID bannerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BannerRegionEstado r where r.bannerId = :bannerId and r.regionIndex = :regionIndex")
    Optional<BannerRegionEstado> buscarParaActualizar(UUID bannerId, int regionIndex);
}
