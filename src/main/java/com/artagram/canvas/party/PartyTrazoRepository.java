package com.artagram.canvas.party;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyTrazoRepository extends JpaRepository<PartyTrazo, UUID> {

    List<PartyTrazo> findBySalaIdAndNumeroRondaOrderBySecuenciaAsc(UUID salaId, int numeroRonda);

    List<PartyTrazo> findBySalaIdOrderBySecuenciaAsc(UUID salaId);
}
