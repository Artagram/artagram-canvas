package com.artagram.canvas.party.dto;

/**
 * Todo lo que viaja por /topic/fiesta/{salaId}: el reloj (TICK), el cierre de
 * ronda, la aparicion/toma/liberacion del borrador/herramientas y los trazos
 * nuevos. El chat de sala usa su propio topic (/topic/fiesta/{salaId}/chat,
 * ver {@link PartyMensajeChatDto}), no este.
 */
public record PartyEventoDto(
        String tipo,
        Long msRestantes,
        PartyTrazoDto trazo,
        String propietarioBorrador,
        String herramienta,
        String propietarioHerramienta
) {
    public static PartyEventoDto tick(long msRestantes) {
        return new PartyEventoDto("TICK", msRestantes, null, null, null, null);
    }

    public static PartyEventoDto rondaTerminada() {
        return new PartyEventoDto("RONDA_TERMINADA", 0L, null, null, null, null);
    }

    public static PartyEventoDto juegoTerminado() {
        return new PartyEventoDto("JUEGO_TERMINADO", 0L, null, null, null, null);
    }

    public static PartyEventoDto borradorDisponible() {
        return new PartyEventoDto("BORRADOR_DISPONIBLE", null, null, null, null, null);
    }

    public static PartyEventoDto borradorTomado(String propietario) {
        return new PartyEventoDto("BORRADOR_TOMADO", null, null, propietario, "BORRADOR", propietario);
    }

    public static PartyEventoDto borradorLiberado() {
        return new PartyEventoDto("BORRADOR_LIBERADO", null, null, null, "BORRADOR", null);
    }

    public static PartyEventoDto herramientaTomada(String herramienta, String propietario) {
        return new PartyEventoDto("HERRAMIENTA_TOMADA", null, null, null, herramienta, propietario);
    }

    public static PartyEventoDto herramientaLiberada(String herramienta) {
        return new PartyEventoDto("HERRAMIENTA_LIBERADA", null, null, null, herramienta, null);
    }

    public static PartyEventoDto trazoNuevo(PartyTrazoDto trazo) {
        return new PartyEventoDto("TRAZO", null, trazo, null, null, null);
    }

    public static PartyEventoDto trazoParcial(PartyTrazoDto trazo) {
        return new PartyEventoDto("TRAZO_PARCIAL", null, trazo, null, null, null);
    }
}
