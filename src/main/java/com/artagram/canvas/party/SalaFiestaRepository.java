package com.artagram.canvas.party;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SalaFiestaRepository extends JpaRepository<SalaFiesta, UUID> {

    Optional<SalaFiesta> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SalaFiesta s where s.id = :id")
    Optional<SalaFiesta> buscarParaActualizar(UUID id);
}
