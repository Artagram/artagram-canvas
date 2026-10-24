package com.artagram.canvas.banner;

import com.artagram.canvas.canvas.Punto;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Convierte trazos vectoriales en pixeles. Es deliberadamente simple (lineas
 * con opacidad, sin texturas de pincel reales) porque aqui el objetivo es la
 * consolidacion del bitmap (HU-16), no el motor de dibujo — ese ya vive en el
 * frontend para el lienzo interactivo.
 */
@Component
public class BannerRasterService {

    public byte[] renderizar(byte[] baseOpcional, int ladoPx, List<BannerTrazo> trazos) {
        BufferedImage imagen = cargarOCrear(baseOpcional, ladoPx);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (BannerTrazo trazo : trazos) {
            dibujarTrazo(g, trazo, ladoPx);
        }
        g.dispose();
        return aPng(imagen);
    }

    public byte[] lienzoEnBlanco(int ladoPx) {
        return aPng(new BufferedImage(ladoPx, ladoPx, BufferedImage.TYPE_INT_ARGB));
    }

    private void dibujarTrazo(Graphics2D g, BannerTrazo trazo, int ladoPx) {
        Color base = Color.decode(normalizarHex(trazo.getColorHex()));
        int alpha = (int) Math.round(trazo.getOpacidad() * 255);
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.min(255, alpha))));
        float grosorPx = (float) (trazo.getGrosor() * ladoPx / 100.0);
        g.setStroke(new BasicStroke(Math.max(1f, grosorPx), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        List<Punto> puntos = trazo.getPuntos();
        for (int i = 0; i < puntos.size() - 1; i++) {
            Punto a = puntos.get(i);
            Punto b = puntos.get(i + 1);
            g.drawLine((int) (a.x() * ladoPx), (int) (a.y() * ladoPx), (int) (b.x() * ladoPx), (int) (b.y() * ladoPx));
        }
        if (puntos.size() == 1) {
            Punto p = puntos.get(0);
            g.fillOval((int) (p.x() * ladoPx - grosorPx / 2), (int) (p.y() * ladoPx - grosorPx / 2),
                    Math.max(1, (int) grosorPx), Math.max(1, (int) grosorPx));
        }
    }

    private String normalizarHex(String colorHex) {
        return colorHex.length() > 7 ? colorHex.substring(0, 7) : colorHex;
    }

    private BufferedImage cargarOCrear(byte[] baseOpcional, int ladoPx) {
        if (baseOpcional == null) {
            return new BufferedImage(ladoPx, ladoPx, BufferedImage.TYPE_INT_ARGB);
        }
        try {
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(baseOpcional));
            return imagen != null ? imagen : new BufferedImage(ladoPx, ladoPx, BufferedImage.TYPE_INT_ARGB);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] aPng(BufferedImage imagen) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
