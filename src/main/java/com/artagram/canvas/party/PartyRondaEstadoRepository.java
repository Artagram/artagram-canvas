package com.artagram.canvas.party;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PartyRondaEstadoRepository extends JpaRepository<PartyRondaEstado, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PartyRondaEstado r where r.salaId = :salaId and r.numeroRonda = :numeroRonda")
    Optional<PartyRondaEstado> buscarParaActualizar(UUID salaId, int numeroRonda);
}
