package com.artagram.canvas.community;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComunidadRepository extends JpaRepository<Comunidad, UUID> {

    boolean existsByNombreIgnoreCase(String nombre);

    Optional<Comunidad> findByNombreIgnoreCase(String nombre);

    /** Base de la cuota del plan gratis: cuántas comunidades ya creó este usuario. */
    long countByCreador_UsuarioId(UUID creadorId);

    /**
     * Comunidades publicas de la gente que comparte comunidad conmigo. Tambien
     * excluye las membresias inactivas: no se recomienda de vuelta una
     * comunidad que el usuario dejo o de la que lo expulsaron.
     */
    @Query("""
            SELECT otra.comunidad.id
            FROM MembresiaComunidad mia, MembresiaComunidad vecino, MembresiaComunidad otra
            WHERE mia.usuario.usuarioId = :usuarioId AND mia.activo = true
              AND vecino.comunidad.id = mia.comunidad.id AND vecino.activo = true
              AND vecino.usuario.usuarioId <> :usuarioId
              AND otra.usuario.usuarioId = vecino.usuario.usuarioId AND otra.activo = true
              AND otra.comunidad.publica = true
              AND otra.comunidad.id NOT IN (SELECT m.comunidad.id FROM MembresiaComunidad m WHERE m.usuario.usuarioId = :usuarioId)
            GROUP BY otra.comunidad.id
            ORDER BY COUNT(DISTINCT vecino.usuario.usuarioId) DESC, otra.comunidad.id
            """)
    List<UUID> buscarPorMiembrosCompartidos(@Param("usuarioId") UUID usuarioId, Pageable pageable);

    @Query("""
            SELECT c.id
            FROM Comunidad c LEFT JOIN MembresiaComunidad m ON m.comunidad = c AND m.activo = true
            WHERE c.publica = true
              AND c.id NOT IN (SELECT x.comunidad.id FROM MembresiaComunidad x WHERE x.usuario.usuarioId = :usuarioId)
            GROUP BY c.id
            ORDER BY COUNT(m) DESC, c.id
            """)
    List<UUID> buscarConMasMiembros(@Param("usuarioId") UUID usuarioId, Pageable pageable);
}
