package com.artagram.canvas.canvas.dto;

import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import java.util.List;
import java.util.UUID;

/**
 * Lo que viaja por /topic/lienzo/{comunidadId}. tipo=PARCIAL son lotes de
 * puntos en vivo mientras alguien dibuja (no se persisten); tipo=FINAL es el
 * trazo ya cerrado con su numero de secuencia asignado por el servidor.
 */
public record TrazoEventoDto(
        String tipo,
        UUID trazoEnProgresoId,
        Long secuencia,
        UUID autorId,
        String autorNombre,
        TipoPincel tipoPincel,
        String colorHex,
        double grosor,
        double opacidad,
        int capa,
        List<Punto> puntos,
        // Id persistido del trazo (FINAL/EDITADO). trazoEnProgresoId es solo el id
        // temporal que genera el cliente al empezar a dibujar; para bloquear o
        // editar se usa este.
        UUID trazoId
) {
    public static TrazoEventoDto parcial(UUID trazoEnProgresoId, UUID autorId, String autorNombre, EnviarPuntosRequest r) {
        return new TrazoEventoDto("PARCIAL", trazoEnProgresoId, null, autorId, autorNombre,
                r.tipoPincel(), r.colorHex(), r.grosor(), r.opacidad(), r.capa(), r.puntos(), null);
    }

    public static TrazoEventoDto finalizado(UUID trazoEnProgresoId, UUID trazoId, long secuencia, UUID autorId, String autorNombre, EnviarPuntosRequest r) {
        return new TrazoEventoDto("FINAL", trazoEnProgresoId, secuencia, autorId, autorNombre,
                r.tipoPincel(), r.colorHex(), r.grosor(), r.opacidad(), r.capa(), r.puntos(), trazoId);
    }
}
