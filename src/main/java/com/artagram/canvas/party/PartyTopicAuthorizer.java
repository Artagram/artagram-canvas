package com.artagram.canvas.party;

import com.artagram.canvas.realtime.DestinationAuthorizer;
import com.artagram.canvas.realtime.StompPrincipal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Solo un participante activo de la sala puede suscribirse o publicar en
 * ella — incluyendo el chat de sala, que cae bajo este mismo patrón de
 * destino ({@code /app|/topic/fiesta/{salaId}/chat...}) sin necesitar
 * autorización propia.
 */
@Component
public class PartyTopicAuthorizer implements DestinationAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(PartyTopicAuthorizer.class);

    private static final Pattern PATRON = Pattern.compile("^/(topic|app)/fiesta/([0-9a-fA-F-]{36})(/.*)?$");

    private final ParticipanteSalaRepository participanteRepository;

    public PartyTopicAuthorizer(ParticipanteSalaRepository participanteRepository) {
        this.participanteRepository = participanteRepository;
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
        UUID salaId = UUID.fromString(matcher.group(2));
        boolean autorizado = participanteRepository.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(salaId, principal.usuarioId());
        if (!autorizado) {
            log.warn("Suscripcion denegada: usuario={} destino={} sala={}", principal.usuarioId(), destino, salaId);
        }
        return autorizado;
    }
}
