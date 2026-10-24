package com.artagram.canvas.party.dto;

import com.artagram.canvas.canvas.TipoPincel;
import java.util.List;

public record EstadoRondaResponse(
        String estadoSala,
        int numeroRonda,
        int totalRondas,
        long msRestantes,
        TipoPincel pincel,
        String colorHex,
        Double grosor,
        Double opacidad,
        String borradorPropietario,
        List<PartyTrazoDto> trazos
) {
}
