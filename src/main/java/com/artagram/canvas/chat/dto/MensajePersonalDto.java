package com.artagram.canvas.chat.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mismo shape que {@code com.artagram.chat.dto.MensajePersonalDto}. El campo
 * {@code entregado} siempre va en {@code true} aqui: este DTO solo viaja por
 * el canal en vivo (ya fue entregado); el estado de lectura real lo sigue
 * llevando artagram-chat en su propia persistencia.
 */
public record MensajePersonalDto(
        UUID id,
        UUID remitenteId,
        String remitenteNombre,
        UUID destinatarioId,
        String contenido,
        boolean entregado,
        Instant creadoEn
) {
}
