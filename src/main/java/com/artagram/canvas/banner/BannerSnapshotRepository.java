package com.artagram.canvas.banner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerSnapshotRepository extends JpaRepository<BannerSnapshot, UUID> {

    Optional<BannerSnapshot> findByBannerIdAndRegionIndex(UUID bannerId, int regionIndex);

    List<BannerSnapshot> findByBannerId(UUID bannerId);
}
