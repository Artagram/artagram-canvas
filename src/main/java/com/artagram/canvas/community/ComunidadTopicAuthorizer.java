package com.artagram.canvas.community;

import com.artagram.canvas.realtime.DestinationAuthorizer;
import com.artagram.canvas.realtime.StompPrincipal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Protege los canales que cuelgan de una comunidad (lienzo, chat; banner
 * llega en un PR siguiente): solo un miembro activo puede suscribirse o
 * publicar. El segmento tras {@code comunidad/} identifica el sub-canal
 * (hoy solo {@code chat}), no cambia el chequeo de membresia.
 */
@Component
public class ComunidadTopicAuthorizer implements DestinationAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(ComunidadTopicAuthorizer.class);

    private static final Pattern PATRON =
            Pattern.compile("^/(topic|app)/(?:lienzo|comunidad)/([0-9a-fA-F-]{36})(/.*)?$");

    private final ComunidadService comunidadService;

    public ComunidadTopicAuthorizer(ComunidadService comunidadService) {
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
        boolean autorizado = comunidadService.esMiembroActivo(principal.usuarioId(), comunidadId);
        if (!autorizado) {
            log.warn("Suscripcion denegada: usuario={} destino={} comunidad={}", principal.usuarioId(), destino, comunidadId);
        }
        return autorizado;
    }
}
