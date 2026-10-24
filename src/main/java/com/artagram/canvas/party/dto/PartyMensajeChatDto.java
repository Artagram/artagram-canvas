package com.artagram.canvas.party.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje del chat de una sala de fiesta, retransmitido por
 * /topic/fiesta/{salaId}/chat. No se persiste: la sala es efímera (ver
 * {@code com.artagram.canvas.party} package-info para la decisión).
 */
public record PartyMensajeChatDto(
        UUID autorId,
        String autorNombre,
        String contenido,
        Instant creadoEn
) {
}
