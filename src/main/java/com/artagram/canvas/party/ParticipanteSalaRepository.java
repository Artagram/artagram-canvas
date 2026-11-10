package com.artagram.canvas.party;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipanteSalaRepository extends JpaRepository<ParticipanteSala, UUID> {

    Optional<ParticipanteSala> findBySala_IdAndUsuario_UsuarioId(UUID salaId, UUID usuarioId);

    List<ParticipanteSala> findBySala_IdAndActivoTrue(UUID salaId);

    long countBySala_IdAndActivoTrue(UUID salaId);

    boolean existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(UUID salaId, UUID usuarioId);

    @Query("select p.sala from ParticipanteSala p where p.usuario.usuarioId = :usuarioId and p.activo = true and p.sala.estado <> com.artagram.canvas.party.EstadoSala.CERRADA order by p.creadoEn desc")
    List<SalaFiesta> findSalasActivasDelUsuario(@Param("usuarioId") UUID usuarioId);

    /**
     * Solo cuenta las salas donde el anfitrion sigue dentro: son las que ve en
     * su lista y puede cerrar. Si contara las que abandono, quedaria bloqueado
     * por una sala que ya no puede encontrar.
     */
    @Query("select count(p) from ParticipanteSala p where p.usuario.usuarioId = :usuarioId and p.activo = true and p.sala.anfitrion.usuarioId = :usuarioId and p.sala.estado <> com.artagram.canvas.party.EstadoSala.CERRADA")
    long contarSalasActivasComoAnfitrion(@Param("usuarioId") UUID usuarioId);
}
