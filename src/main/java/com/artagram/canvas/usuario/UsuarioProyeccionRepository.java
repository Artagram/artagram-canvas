package com.artagram.canvas.usuario;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioProyeccionRepository extends JpaRepository<UsuarioProyeccion, UUID> {

    /**
     * Solo encuentra usuarios que ya interactuaron con este servicio (o cuyo
     * evento de registro ya llegó). Para "invitar por nombre de artista" esto
     * es una limitación real: alguien que nunca tocó canvas/fiesta no aparece
     * aquí todavía. Migrar esto a una búsqueda contra artagram-users queda
     * fuera de este scope.
     */
    Optional<UsuarioProyeccion> findByNombreArtistaIgnoreCase(String nombreArtista);
}
