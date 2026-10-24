package com.artagram.canvas.chat.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mismo shape que {@code com.artagram.chat.dto.MensajeComunidadDto} en
 * artagram-chat (dueño real de la persistencia) — se mantiene idéntico a
 * propósito para que el frontend no note de qué servicio vino el mensaje.
 */
public record MensajeComunidadDto(
        UUID id,
        UUID remitenteId,
        String remitenteNombre,
        String contenido,
        Instant creadoEn
) {
}
