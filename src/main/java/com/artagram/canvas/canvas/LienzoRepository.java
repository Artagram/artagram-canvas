package com.artagram.canvas.canvas;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LienzoRepository extends JpaRepository<Lienzo, UUID> {

    Optional<Lienzo> findByComunidadId(UUID comunidadId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lienzo l where l.comunidadId = :comunidadId")
    Optional<Lienzo> buscarParaActualizar(UUID comunidadId);
}
