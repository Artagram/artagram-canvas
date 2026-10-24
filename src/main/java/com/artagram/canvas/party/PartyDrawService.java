package com.artagram.canvas.party;

import com.artagram.canvas.canvas.TipoPincel;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.common.lock.ConcurrencyMetrics;
import com.artagram.canvas.common.lock.DistributedLockService;
import com.artagram.canvas.party.dto.BloqueoBorradorResponse;
import com.artagram.canvas.party.dto.BloqueoHerramientaResponse;
import com.artagram.canvas.party.dto.PartyEventoDto;
import com.artagram.canvas.party.dto.PartyTrazoDto;
import com.artagram.canvas.party.dto.TrazarEnRondaRequest;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dibujar durante la ronda (todos con el mismo pincel, append-only) y pelear
 * por el borrador (exclusion mutua con compare-and-set).
 */
@Service
public class PartyDrawService {

    private static final Logger log = LoggerFactory.getLogger(PartyDrawService.class);
    private static final Duration TTL_BORRADOR = Duration.ofSeconds(10);
    private static final String SEPARADOR = "::";

    private final Map<String, Long> cacheParticipantes = new ConcurrentHashMap<>();
    private final Map<UUID, List<PartyTrazoDto>> bufferTrazos = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> secuenciasSalas = new ConcurrentHashMap<>();

    private final SalaFiestaRepository salaRepository;
    private final ParticipanteSalaRepository participanteRepository;
    private final PartyTrazoRepository partyTrazoRepository;
    private final UsuarioProyeccionResolver usuarioResolver;
    private final DistributedLockService lockService;
    private final ConcurrencyMetrics metrics;
    private final RealtimeBroadcaster broadcaster;

    public PartyDrawService(SalaFiestaRepository salaRepository, ParticipanteSalaRepository participanteRepository,
                             PartyTrazoRepository partyTrazoRepository, UsuarioProyeccionResolver usuarioResolver,
                             DistributedLockService lockService, ConcurrencyMetrics metrics,
                             RealtimeBroadcaster broadcaster) {
        this.salaRepository = salaRepository;
        this.participanteRepository = participanteRepository;
        this.partyTrazoRepository = partyTrazoRepository;
        this.usuarioResolver = usuarioResolver;
        this.lockService = lockService;
        this.metrics = metrics;
        this.broadcaster = broadcaster;
    }

    public void trazar(UUID salaId, UUID usuarioId, String nombreUsuario, TrazarEnRondaRequest request) {
        if (!esParticipanteValido(salaId, usuarioId)) {
            return;
        }
        SalaFiesta sala = salaRepository.findById(salaId)
                .orElseThrow(() -> ApiException.notFound("SALA_NO_ENCONTRADA", "Esa sala no existe"));
        long secuencia = siguienteSecuenciaLocal(salaId);
        TipoPincel pincel = request.tipoPincel() != null ? request.tipoPincel() : sala.getPincelActual();
        String color = request.colorHex() != null ? request.colorHex() : sala.getColorActual();
        double grosor = request.grosor() != null ? request.grosor() : (sala.getGrosorActual() != null ? sala.getGrosorActual() : 20.0);
        double opacidad = request.opacidad() != null ? request.opacidad() : (sala.getOpacidadActual() != null ? sala.getOpacidadActual() : 1.0);
        PartyTrazoDto dto = new PartyTrazoDto(secuencia, usuarioId, nombreUsuario, false,
                pincel, color, grosor, opacidad, request.puntos());
        bufferTrazos.computeIfAbsent(salaId, k -> new CopyOnWriteArrayList<>()).add(dto);
        broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.trazoNuevo(dto));
    }

    public BloqueoBorradorResponse tomarBorrador(UUID salaId, UUID usuarioId, String nombreUsuario) {
        if (!participanteRepository.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(salaId, usuarioId)) {
            throw ApiException.forbidden("NO_ERES_PARTICIPANTE", "No perteneces a esta sala");
        }
        String clave = claveBorrador(salaId);
        String propietario = propietario(usuarioId, nombreUsuario);
        boolean adquirido = lockService.adquirir(clave, propietario, TTL_BORRADOR);
        if (adquirido) {
            broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.borradorTomado(nombreUsuario));
            return new BloqueoBorradorResponse(true, nombreUsuario);
        }
        metrics.registrarBloqueoNegado();
        metrics.registrarDisputaBorrador();
        String actual = lockService.propietarioActual(clave).map(this::extraerNombre).orElse("alguien");
        return new BloqueoBorradorResponse(false, actual);
    }

    private boolean esParticipanteValido(UUID salaId, UUID usuarioId) {
        String clave = salaId + ":" + usuarioId;
        Long expira = cacheParticipantes.get(clave);
        long ahora = System.currentTimeMillis();
        if (expira != null && ahora < expira) {
            return true;
        }
        boolean valido = participanteRepository.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(salaId, usuarioId);
        if (valido) {
            cacheParticipantes.put(clave, ahora + 30_000L);
        } else {
            cacheParticipantes.remove(clave);
        }
        return valido;
    }

    public void parcial(UUID salaId, UUID usuarioId, String nombreUsuario, TrazarEnRondaRequest request) {
        if (!esParticipanteValido(salaId, usuarioId)) {
            return;
        }
        boolean esBorrador = request.tipoPincel() == null;
        PartyTrazoDto dto = new PartyTrazoDto(0, usuarioId, nombreUsuario, esBorrador,
                request.tipoPincel(), request.colorHex(), request.grosor(), request.opacidad(), request.puntos());
        broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.trazoParcial(dto));
    }

    public void borrar(UUID salaId, UUID usuarioId, String nombreUsuario, TrazarEnRondaRequest request) {
        String actualHerramienta = lockService.propietarioActual(claveHerramienta(salaId, "BORRADOR")).orElse(null);
        String actualBorrador = lockService.propietarioActual(claveBorrador(salaId)).orElse(null);
        String miPropietario = propietario(usuarioId, nombreUsuario);
        if (!miPropietario.equals(actualHerramienta) && !miPropietario.equals(actualBorrador)) {
            throw ApiException.forbidden("NO_TIENES_EL_BORRADOR", "No tienes el borrador en este momento");
        }
        long secuencia = siguienteSecuenciaLocal(salaId);
        PartyTrazoDto dto = new PartyTrazoDto(secuencia, usuarioId, nombreUsuario, true,
                null, null, request.grosor() != null ? request.grosor() : 40.0, 1.0, request.puntos());
        bufferTrazos.computeIfAbsent(salaId, k -> new CopyOnWriteArrayList<>()).add(dto);
        broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.trazoNuevo(dto));
    }

    private long siguienteSecuenciaLocal(UUID salaId) {
        return secuenciasSalas.computeIfAbsent(salaId, k -> {
            long maxDb = partyTrazoRepository.findBySalaIdOrderBySecuenciaAsc(salaId).stream()
                    .mapToLong(PartyTrazo::getSecuencia)
                    .max()
                    .orElse(0L);
            long maxBuffer = bufferTrazos.getOrDefault(salaId, List.of()).stream()
                    .mapToLong(PartyTrazoDto::secuencia)
                    .max()
                    .orElse(0L);
            return new AtomicLong(Math.max(maxDb, maxBuffer));
        }).incrementAndGet();
    }

    public List<PartyTrazoDto> obtenerTrazos(UUID salaId, int numeroRonda) {
        List<PartyTrazoDto> enDb = partyTrazoRepository.findBySalaIdOrderBySecuenciaAsc(salaId).stream()
                .map(t -> new PartyTrazoDto(t.getSecuencia(), t.getAutor().getUsuarioId(), t.getAutor().getNombreArtista(),
                        t.isBorrador(), t.getTipoPincel(), t.getColorHex(), t.getGrosor(), t.getOpacidad(), t.getPuntos()))
                .toList();
        List<PartyTrazoDto> enMemoria = bufferTrazos.getOrDefault(salaId, List.of());
        if (enMemoria.isEmpty()) {
            return enDb;
        }
        Map<Long, PartyTrazoDto> mapa = new LinkedHashMap<>();
        for (PartyTrazoDto t : enDb) {
            mapa.put(t.secuencia(), t);
        }
        for (PartyTrazoDto t : enMemoria) {
            mapa.put(t.secuencia(), t);
        }
        return new ArrayList<>(mapa.values());
    }

    @Transactional
    public void consolidarTrazosEnBaseDeDatos(UUID salaId) {
        List<PartyTrazoDto> pendientes = bufferTrazos.remove(salaId);
        if (pendientes == null || pendientes.isEmpty()) {
            return;
        }
        SalaFiesta sala = salaRepository.findById(salaId).orElse(null);
        if (sala == null) {
            return;
        }
        List<PartyTrazo> entidades = new ArrayList<>();
        for (PartyTrazoDto dto : pendientes) {
            UsuarioProyeccion autor = usuarioResolver.referencia(dto.autorId());
            entidades.add(new PartyTrazo(salaId, sala.getNumeroRonda(), dto.secuencia(), autor,
                    dto.borrador(), dto.tipoPincel(), dto.colorHex(), dto.grosor(), dto.opacidad(), dto.puntos()));
        }
        partyTrazoRepository.saveAll(entidades);
        log.info("Trazos consolidados en DB para sala {}: {} trazos guardados", salaId, entidades.size());
    }

    public void liberarBorrador(UUID salaId, UUID usuarioId, String nombreUsuario) {
        String propietario = propietario(usuarioId, nombreUsuario);
        lockService.liberar(claveHerramienta(salaId, "BORRADOR"), propietario);
        if (lockService.liberar(claveBorrador(salaId), propietario)) {
            broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.borradorLiberado());
        }
    }

    public BloqueoHerramientaResponse tomarHerramienta(UUID salaId, UUID usuarioId, String nombreUsuario, String herramienta) {
        if (!participanteRepository.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(salaId, usuarioId)) {
            throw ApiException.forbidden("NO_ERES_PARTICIPANTE", "No perteneces a esta sala");
        }
        String clave = claveHerramienta(salaId, herramienta);
        String propietario = propietario(usuarioId, nombreUsuario);
        boolean adquirido = lockService.adquirir(clave, propietario, Duration.ofSeconds(60));
        if (adquirido) {
            if ("BORRADOR".equalsIgnoreCase(herramienta)) {
                lockService.adquirir(claveBorrador(salaId), propietario, Duration.ofSeconds(60));
            }
            broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.herramientaTomada(herramienta.toUpperCase(), nombreUsuario));
            return new BloqueoHerramientaResponse(true, herramienta.toUpperCase(), nombreUsuario);
        }
        metrics.registrarBloqueoNegado();
        String actual = lockService.propietarioActual(clave).map(this::extraerNombre).orElse("alguien");
        return new BloqueoHerramientaResponse(false, herramienta.toUpperCase(), actual);
    }

    public void liberarHerramienta(UUID salaId, UUID usuarioId, String nombreUsuario, String herramienta) {
        String clave = claveHerramienta(salaId, herramienta);
        String propietario = propietario(usuarioId, nombreUsuario);
        if ("BORRADOR".equalsIgnoreCase(herramienta)) {
            lockService.liberar(claveBorrador(salaId), propietario);
        }
        if (lockService.liberar(clave, propietario)) {
            broadcaster.enviar("/topic/fiesta/" + salaId, PartyEventoDto.herramientaLiberada(herramienta.toUpperCase()));
        }
    }

    public Map<String, String> consultarHerramientas(UUID salaId) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (String h : List.of("LAPIZ", "MARCADOR", "ACUARELA", "PLUMA", "AEROGRAFO", "BORRADOR")) {
            lockService.propietarioActual(claveHerramienta(salaId, h))
                    .ifPresent(p -> mapa.put(h, extraerNombre(p)));
        }
        return mapa;
    }

    private String claveHerramienta(UUID salaId, String herramienta) {
        return "fiesta-herramienta:" + salaId + ":" + herramienta.toUpperCase();
    }

    private String claveBorrador(UUID salaId) {
        return "fiesta-borrador:" + salaId;
    }

    private String propietario(UUID usuarioId, String nombreUsuario) {
        return usuarioId + SEPARADOR + nombreUsuario;
    }

    private String extraerNombre(String propietario) {
        int idx = propietario.indexOf(SEPARADOR);
        return idx >= 0 ? propietario.substring(idx + SEPARADOR.length()) : propietario;
    }
}
