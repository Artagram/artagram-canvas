package com.artagram.canvas.party;

import com.artagram.canvas.canvas.TipoPincel;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** La sala reparte el mismo pincel para todos en cada ronda. */
@Component
public class SeleccionadorPincelRonda {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String[] PALETA = {
            "#2E5E8C", "#E63946", "#2A9D8F", "#F4A261", "#8338EC", "#FF006E", "#FFBE0B", "#3A86FF"
    };

    public record EstiloRonda(TipoPincel pincel, String colorHex, double grosor, double opacidad) {
    }

    public EstiloRonda seleccionar() {
        TipoPincel pincel = TipoPincel.values()[RANDOM.nextInt(TipoPincel.values().length)];
        String color = PALETA[RANDOM.nextInt(PALETA.length)];
        double grosor = 6 + RANDOM.nextInt(20);
        double opacidad = (pincel == TipoPincel.ACUARELA || pincel == TipoPincel.AEROGRAFO) ? 0.5 : 1.0;
        return new EstiloRonda(pincel, color, grosor, opacidad);
    }
}
