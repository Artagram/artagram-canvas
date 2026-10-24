package com.artagram.canvas.community;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComunidadRepository extends JpaRepository<Comunidad, UUID> {

    boolean existsByNombreIgnoreCase(String nombre);

    Optional<Comunidad> findByNombreIgnoreCase(String nombre);

    /** Base de la cuota del plan gratis: cuántas comunidades ya creó este usuario. */
    long countByCreador_UsuarioId(UUID creadorId);
}
