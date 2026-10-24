package com.artagram.canvas.banner;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerComunitarioRepository extends JpaRepository<BannerComunitario, UUID> {

    Optional<BannerComunitario> findByComunidadId(UUID comunidadId);
}
