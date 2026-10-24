package com.artagram.canvas.party;

import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.party.dto.CrearSalaRequest;
import com.artagram.canvas.party.dto.EstadoRondaResponse;
import com.artagram.canvas.party.dto.PartyEventoDto;
import com.artagram.canvas.party.dto.PartyTrazoDto;
import com.artagram.canvas.party.dto.SalaFiestaResponse;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Salas de modo fiesta y control de sus rondas. El estado "quien esta en la
 * sala" y su configuracion vive en Postgres (el respaldo); el reloj vivo de
 * la ronda vive en Redis via PartyRoundClock.
 * <p>
 * A diferencia del monolito, este servicio no reserva/libera un cupo de
 * "salas de fiesta activas" contra artagram-users: esa cuota nunca llegó a
 * tener un límite real (el código original la incrementaba sin tope alguno,
 * solo para analítica) y el esquema actual de {@code uso_cuenta} en
 * artagram-users ni siquiera tiene esas columnas — son de almacenamiento, no
 * de salas de fiesta. Si se decide medir esto, se agrega como un endpoint
 * interno nuevo en artagram-users; no es parte de este scaffold.
 */
@Service
public class SalaFiestaService {

    private static final Logger log = LoggerFactory.getLogger(SalaFiestaService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SalaFiestaRepository salaRepository;
    private final ParticipanteSalaRepository participanteRepository;
    private final PartyRondaEstadoRepository rondaEstadoRepository;
    private final PartyDrawService partyDrawService;
    private final UsuarioProyeccionResolver usuarioResolver;
    private final PartyRoundClock roundClock;
    private final SeleccionadorPincelRonda seleccionadorPincel;
    private final RealtimeBroadcaster broadcaster;

    public SalaFiestaService(SalaFiestaRepository salaRepository, ParticipanteSalaRepository participanteRepository,
                              PartyRondaEstadoRepository rondaEstadoRepository, PartyDrawService partyDrawService,
                              UsuarioProyeccionResolver usuarioResolver, PartyRoundClock roundClock,
                              SeleccionadorPincelRonda seleccionadorPincel, RealtimeBroadcaster broadcaster) {
        this.salaRepository = salaRepository;
        this.participanteRepository = participanteRepository;
        this.rondaEstadoRepository = rondaEstadoRepository;
        this.partyDrawService = partyDrawService;
        this.usuarioResolver = usuarioResolver;
        this.roundClock = roundClock;
        this.seleccionadorPincel = seleccionadorPincel;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public SalaFiestaResponse crear(com.artagram.canvas.auth.JwtService.TokenPrincipal principal, CrearSalaRequest request) {
        UsuarioProyeccion anfitrion = usuarioResolver.resolver(principal);
        String codigo = generarCodigoUnico();
        int totalRondas = request.totalRondas() != null ? request.totalRondas() : 3;
        SalaFiesta sala = salaRepository.save(new SalaFiesta(codigo, anfitrion, request.duracionRondaSegundos(), request.cupoMaximo(), totalRondas));
        participanteRepository.save(new ParticipanteSala(sala, anfitrion));
        log.info("Sala de fiesta creada: id={} codigo={} anfitrion={} cupo={} totalRondas={}",
                sala.getId(), codigo, anfitrion.getUsuarioId(), request.cupoMaximo(), totalRondas);
        return aRespuesta(sala);
    }

    @Transactional
    public SalaFiestaResponse unirse(com.artagram.canvas.auth.JwtService.TokenPrincipal principal, String codigo) {
        SalaFiesta sala = salaRepository.findByCodigo(codigo.toUpperCase())
                .orElseThrow(() -> ApiException.notFound("SALA_NO_ENCONTRADA", "No existe una sala con ese codigo"));
        Optional<ParticipanteSala> existente = participanteRepository.findBySala_IdAndUsuario_UsuarioId(sala.getId(), principal.usuarioId());
        if (existente.isPresent() && existente.get().isActivo()) {
            return aRespuesta(sala);
        }
        long activos = participanteRepository.countBySala_IdAndActivoTrue(sala.getId());
        if (activos >= sala.getCupoMaximo()) {
            throw ApiException.conflict("SALA_LLENA", "La sala esta llena");
        }
        if (existente.isPresent()) {
            existente.get().reactivar();
        } else {
            UsuarioProyeccion usuario = usuarioResolver.resolver(principal);
            participanteRepository.save(new ParticipanteSala(sala, usuario));
        }
        return aRespuesta(sala);
    }

    @Transactional
    public void iniciarRonda(UUID usuarioId, UUID salaId) {
        SalaFiesta sala = salaRepository.buscarParaActualizar(salaId)
                .orElseThrow(() -> ApiException.notFound("SALA_NO_ENCONTRADA", "Esa sala no existe"));
        if (!sala.getAnfitrion().getUsuarioId().equals(usuarioId)) {
            throw ApiException.forbidden("SOLO_ANFITRION", "Solo el anfitrion puede iniciar la ronda");
        }
        if (sala.getNumeroRonda() >= sala.getTotalRondas()) {
            throw ApiException.conflict("PARTIDA_TERMINADA", "La partida ya alcanzo el maximo de rondas");
        }
        SeleccionadorPincelRonda.EstiloRonda estilo = seleccionadorPincel.seleccionar();
        sala.iniciarNuevaRonda(estilo.pincel(), estilo.colorHex(), estilo.grosor(), estilo.opacidad());
        rondaEstadoRepository.save(new PartyRondaEstado(sala.getId(), sala.getNumeroRonda()));
        roundClock.iniciarRonda(sala.getId(), sala.getDuracionRondaSegundos() * 1000L);
        log.info("Ronda {}/{} iniciada en sala {}: pincel={} color={}", sala.getNumeroRonda(), sala.getTotalRondas(), salaId, estilo.pincel(), estilo.colorHex());
    }

    @Transactional
    public void terminarRonda(UUID salaId) {
        salaRepository.buscarParaActualizar(salaId).ifPresent(sala -> {
            partyDrawService.consolidarTrazosEnBaseDeDatos(salaId);
            if (sala.getNumeroRonda() >= sala.getTotalRondas()) {
                sala.setEstado(EstadoSala.CERRADA);
            } else {
                sala.congelar();
            }
        });
        roundClock.terminarRonda(salaId);
        log.info("Ronda terminada en sala {}", salaId);
    }

    @Transactional(readOnly = true)
    public EstadoRondaResponse obtenerEstado(UUID salaId, String propietarioBorrador) {
        SalaFiesta sala = salaRepository.findById(salaId)
                .orElseThrow(() -> ApiException.notFound("SALA_NO_ENCONTRADA", "Esa sala no existe"));
        List<PartyTrazoDto> trazos = partyDrawService.obtenerTrazos(salaId, sala.getNumeroRonda());
        long msRestantes = sala.getEstado() == EstadoSala.EN_RONDA ? roundClock.msRestantes(salaId) : 0;
        return new EstadoRondaResponse(sala.getEstado().name(), sala.getNumeroRonda(), sala.getTotalRondas(), msRestantes,
                sala.getPincelActual(), sala.getColorActual(), sala.getGrosorActual(), sala.getOpacidadActual(),
                propietarioBorrador, trazos);
    }

    @Transactional(readOnly = true)
    public boolean esParticipanteActivo(UUID usuarioId, UUID salaId) {
        return participanteRepository.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(salaId, usuarioId);
    }

    @Transactional(readOnly = true)
    public List<SalaFiestaResponse> listarSalasActivasDelUsuario(UUID usuarioId) {
        return participanteRepository.findSalasActivasDelUsuario(usuarioId).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional
    public void cerrarSala(UUID usuarioId, UUID salaId) {
        SalaFiesta sala = salaRepository.findById(salaId)
                .orElseThrow(() -> ApiException.notFound("SALA_NO_ENCONTRADA", "Esa sala no existe"));
        if (!sala.getAnfitrion().getUsuarioId().equals(usuarioId)) {
            throw ApiException.forbidden("SOLO_ANFITRION", "Solo el anfitrión puede cerrar la sala");
        }
        partyDrawService.consolidarTrazosEnBaseDeDatos(salaId);
        sala.setEstado(EstadoSala.CERRADA);
        roundClock.terminarRonda(salaId);
        broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.juegoTerminado());
        log.info("Sala cerrada por anfitrión: id={}", salaId);
    }

    @Transactional
    public void salirDeSala(UUID usuarioId, UUID salaId) {
        ParticipanteSala participante = participanteRepository.findBySala_IdAndUsuario_UsuarioId(salaId, usuarioId)
                .orElseThrow(() -> ApiException.notFound("NO_ERES_PARTICIPANTE", "No estás en esta sala"));
        participante.salir();
        partyDrawService.consolidarTrazosEnBaseDeDatos(salaId);
        log.info("Usuario {} salió de la sala {}", usuarioId, salaId);
    }

    @Transactional
    public int limpiarSalasInactivas(UUID usuarioId) {
        List<SalaFiesta> salas = participanteRepository.findSalasActivasDelUsuario(usuarioId);
        int cerradas = 0;
        for (SalaFiesta s : salas) {
            partyDrawService.consolidarTrazosEnBaseDeDatos(s.getId());
            if (s.getAnfitrion().getUsuarioId().equals(usuarioId)) {
                s.setEstado(EstadoSala.CERRADA);
                roundClock.terminarRonda(s.getId());
                broadcaster.enviar("/topic/fiesta/" + s.getId(), PartyEventoDto.juegoTerminado());
                cerradas++;
            } else {
                participanteRepository.findBySala_IdAndUsuario_UsuarioId(s.getId(), usuarioId)
                        .ifPresent(ParticipanteSala::salir);
                cerradas++;
            }
        }
        return cerradas;
    }

    @Transactional(readOnly = true)
    public SalaFiestaResponse obtenerRespuesta(UUID salaId) {
        SalaFiesta sala = salaRepository.findById(salaId)
                .orElseThrow(() -> ApiException.notFound("SALA_NO_ENCONTRADA", "Esa sala no existe"));
        return aRespuesta(sala);
    }

    void notificarFinDeRonda(UUID salaId) {
        salaRepository.findById(salaId).ifPresentOrElse(sala -> {
            if (sala.getNumeroRonda() >= sala.getTotalRondas() || sala.getEstado() == EstadoSala.CERRADA) {
                broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.juegoTerminado());
            } else {
                broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.rondaTerminada());
            }
        }, () -> broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.rondaTerminada()));
    }

    void notificarTick(UUID salaId, long msRestantes) {
        broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.tick(msRestantes));
    }

    void notificarBorradorDisponible(UUID salaId) {
        broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.borradorDisponible());
    }

    private String generarCodigoUnico() {
        String codigo;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(ALFABETO_CODIGO.charAt(RANDOM.nextInt(ALFABETO_CODIGO.length())));
            }
            codigo = sb.toString();
        } while (salaRepository.existsByCodigo(codigo));
        return codigo;
    }

    private SalaFiestaResponse aRespuesta(SalaFiesta sala) {
        long total = participanteRepository.countBySala_IdAndActivoTrue(sala.getId());
        return new SalaFiestaResponse(sala.getId(), sala.getCodigo(), sala.getAnfitrion().getNombreArtista(),
                sala.getDuracionRondaSegundos(), sala.getCupoMaximo(), sala.getEstado().name(), total, sala.getNumeroRonda(), sala.getTotalRondas());
    }
}
