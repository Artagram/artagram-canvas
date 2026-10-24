package com.artagram.canvas.banner;

import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.realtime.DestinationAuthorizer;
import com.artagram.canvas.realtime.StompPrincipal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Protege los canales del banner comunitario. A diferencia de
 * {@code ComunidadTopicAuthorizer} (solo miembros), aquí también se permite
 * sin membresía si la comunidad es pública — igual regla que
 * {@code BannerController.obtenerEstado}, para que ver/seguir un banner
 * público por WebSocket no exija unirse primero. Nótese que el monolito no
 * tenía ningún autorizador para este canal (cualquier sesión autenticada
 * podía suscribirse); este chequeo es más estricto, no una regresión.
 */
@Component
public class BannerTopicAuthorizer implements DestinationAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(BannerTopicAuthorizer.class);

    private static final Pattern PATRON = Pattern.compile("^/(topic|app)/banner/([0-9a-fA-F-]{36})(/.*)?$");

    private final ComunidadService comunidadService;

    public BannerTopicAuthorizer(ComunidadService comunidadService) {
        this.comunidadService = comunidadService;
    }

    @Override
    public boolean soporta(String destino) {
        return PATRON.matcher(destino).matches();
    }

    @Override
    public boolean autorizado(StompPrincipal principal, String destino) {
        Matcher matcher = PATRON.matcher(destino);
        if (!matcher.matches()) {
            return false;
        }
        UUID comunidadId = UUID.fromString(matcher.group(2));
        boolean autorizado = comunidadService.esMiembroActivo(principal.usuarioId(), comunidadId)
                || comunidadService.ver(comunidadId, principal.usuarioId()).publica();
        if (!autorizado) {
            log.warn("Suscripcion denegada: usuario={} destino={} comunidad={}", principal.usuarioId(), destino, comunidadId);
        }
        return autorizado;
    }
}
