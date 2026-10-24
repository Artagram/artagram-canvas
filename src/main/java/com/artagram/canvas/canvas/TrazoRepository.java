package com.artagram.canvas.canvas;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrazoRepository extends JpaRepository<Trazo, UUID> {

    List<Trazo> findByLienzoIdAndSecuenciaGreaterThanOrderBySecuenciaAsc(UUID lienzoId, long secuencia);

    long countByLienzoId(UUID lienzoId);
}
