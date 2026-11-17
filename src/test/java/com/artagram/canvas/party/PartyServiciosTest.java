package com.artagram.canvas.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.auth.JwtService.TokenPrincipal;
import com.artagram.canvas.canvas.TipoPincel;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.party.dto.PartyEventoDto;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

class PartyServiciosTest {

    private final UUID anfitrionId = UUID.randomUUID();
    private final UUID invitadoId = UUID.randomUUID();
    private final UUID salaId = UUID.randomUUID();
    private final UsuarioProyeccion anfitrion = new UsuarioProyeccion(anfitrionId, "ana", true);

    private SalaFiestaRepository salas;
    private ParticipanteSalaRepository participantes;
    private PartyRondaEstadoRepository rondas;
    private PartyDrawService draw;
    private UsuarioProyeccionResolver usuarios;
    private PartyRoundClock reloj;
    private RealtimeBroadcaster broadcaster;
    private SalaFiestaService service;
    private SalaFiesta sala;

    @BeforeEach
    void setUp() {
        salas = mock(SalaFiestaRepository.class);
        participantes = mock(ParticipanteSalaRepository.class);
        rondas = mock(PartyRondaEstadoRepository.class);
        draw = mock(PartyDrawService.class);
        usuarios = mock(UsuarioProyeccionResolver.class);
        reloj = mock(PartyRoundClock.class);
        broadcaster = mock(RealtimeBroadcaster.class);
        service = new SalaFiestaService(salas, participantes, rondas, draw, usuarios, reloj,
                new SeleccionadorPincelRonda(), broadcaster, new PartyProperties());
        sala = new SalaFiesta("ABC123", anfitrion, 30, 2, 2);
        ReflectionTestUtils.setField(sala, "id", salaId);
        when(salas.findById(salaId)).thenReturn(Optional.of(sala));
        when(salas.buscarParaActualizar(salaId)).thenReturn(Optional.of(sala));
        when(salas.findByCodigo("ABC123")).thenReturn(Optional.of(sala));
    }

    private TokenPrincipal invitado() {
        return new TokenPrincipal(invitadoId, "sam", "USUARIO", false);
    }

    private String ultimoTipo() {
        ArgumentCaptor<Object> c = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster, org.mockito.Mockito.atLeastOnce()).enviar(eq("/topic/fiesta/" + salaId), c.capture());
        return ((PartyEventoDto) c.getValue()).tipo();
    }

    @Test
    void crearConRondasPorDefectoYCodigoUnico() {
        when(usuarios.resolver(any(TokenPrincipal.class))).thenReturn(anfitrion);
        when(salas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salas.existsByCodigo(anyString())).thenReturn(true, false);

        var r = service.crear(new TokenPrincipal(anfitrionId, "ana", "USUARIO", true),
                new com.artagram.canvas.party.dto.CrearSalaRequest(30, 4, null));

        assertThat(r.totalRondas()).isEqualTo(3);
        assertThat(r.codigo()).hasSize(6);
        verify(salas, times(2)).existsByCodigo(anyString());
    }

    @Test
    void unirseSegunCupoYParticipacionPrevia() {
        when(participantes.findBySala_IdAndUsuario_UsuarioId(salaId, invitadoId)).thenReturn(Optional.empty());
        when(participantes.countBySala_IdAndActivoTrue(salaId)).thenReturn(1L);
        when(usuarios.resolver(any(TokenPrincipal.class))).thenReturn(new UsuarioProyeccion(invitadoId, "sam", false));
        assertThat(service.unirse(invitado(), "abc123").codigo()).isEqualTo("ABC123");
        verify(participantes).save(any(ParticipanteSala.class));

        ParticipanteSala previo = new ParticipanteSala(sala, new UsuarioProyeccion(invitadoId, "sam", false));
        when(participantes.findBySala_IdAndUsuario_UsuarioId(salaId, invitadoId)).thenReturn(Optional.of(previo));
        service.unirse(invitado(), "ABC123");
        previo.salir();
        assertThat(previo.isActivo()).isFalse();
        service.unirse(invitado(), "ABC123");
        assertThat(previo.isActivo()).isTrue();

        previo.salir();
        when(participantes.countBySala_IdAndActivoTrue(salaId)).thenReturn(2L);
        assertThatThrownBy(() -> service.unirse(invitado(), "ABC123")).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("SALA_LLENA");
        assertThatThrownBy(() -> service.unirse(invitado(), "zzz")).isInstanceOf(ApiException.class);
    }

    @Test
    void rondasHastaTerminarLaPartida() {
        assertThatThrownBy(() -> service.iniciarRonda(invitadoId, salaId)).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("SOLO_ANFITRION");

        service.iniciarRonda(anfitrionId, salaId);
        assertThat(sala.getEstado()).isEqualTo(EstadoSala.EN_RONDA);
        verify(reloj).iniciarRonda(salaId, 30_000L);
        when(reloj.msRestantes(salaId)).thenReturn(1234L);
        assertThat(service.obtenerEstado(salaId, "ana").msRestantes()).isEqualTo(1234L);

        service.terminarRonda(salaId);
        assertThat(service.obtenerEstado(salaId, null).msRestantes()).isZero();
        service.notificarFinDeRonda(salaId);
        assertThat(ultimoTipo()).isEqualTo("RONDA_TERMINADA");

        service.iniciarRonda(anfitrionId, salaId);
        service.terminarRonda(salaId);
        assertThat(sala.getEstado()).isEqualTo(EstadoSala.CERRADA);
        service.notificarFinDeRonda(salaId);
        assertThat(ultimoTipo()).isEqualTo("JUEGO_TERMINADO");
        assertThatThrownBy(() -> service.iniciarRonda(anfitrionId, salaId)).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("PARTIDA_TERMINADA");

        UUID otra = UUID.randomUUID();
        when(salas.buscarParaActualizar(otra)).thenReturn(Optional.empty());
        when(salas.findById(otra)).thenReturn(Optional.empty());
        service.terminarRonda(otra);
        service.notificarFinDeRonda(otra);
        assertThatThrownBy(() -> service.iniciarRonda(anfitrionId, otra)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.obtenerEstado(otra, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.obtenerRespuesta(otra)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.cerrarSala(anfitrionId, otra)).isInstanceOf(ApiException.class);
    }

    @Test
    void cerrarSalirYLimpiarSalas() {
        assertThatThrownBy(() -> service.cerrarSala(invitadoId, salaId)).isInstanceOf(ApiException.class);
        service.cerrarSala(anfitrionId, salaId);
        assertThat(sala.getEstado()).isEqualTo(EstadoSala.CERRADA);
        assertThat(ultimoTipo()).isEqualTo("JUEGO_TERMINADO");

        ParticipanteSala yo = new ParticipanteSala(sala, new UsuarioProyeccion(invitadoId, "sam", false));
        when(participantes.findBySala_IdAndUsuario_UsuarioId(salaId, invitadoId)).thenReturn(Optional.of(yo));
        service.salirDeSala(invitadoId, salaId);
        assertThat(yo.isActivo()).isFalse();
        assertThatThrownBy(() -> service.salirDeSala(UUID.randomUUID(), salaId)).isInstanceOf(ApiException.class);

        SalaFiesta ajena = new SalaFiesta("XYZ999", new UsuarioProyeccion(UUID.randomUUID(), "x", false), 30, 4, 1);
        ReflectionTestUtils.setField(ajena, "id", UUID.randomUUID());
        when(participantes.findSalasActivasDelUsuario(anfitrionId)).thenReturn(List.of(sala, ajena));
        when(participantes.findBySala_IdAndUsuario_UsuarioId(ajena.getId(), anfitrionId)).thenReturn(Optional.empty());
        assertThat(service.limpiarSalasInactivas(anfitrionId)).isEqualTo(2);
        assertThat(service.listarSalasActivasDelUsuario(anfitrionId)).hasSize(2);
        assertThat(service.obtenerRespuesta(salaId).anfitrionNombre()).isEqualTo("ana");
        when(participantes.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(salaId, anfitrionId)).thenReturn(true);
        assertThat(service.esParticipanteActivo(anfitrionId, salaId)).isTrue();

        service.notificarTick(salaId, 500);
        assertThat(ultimoTipo()).isEqualTo("TICK");
        service.notificarBorradorDisponible(salaId);
        assertThat(ultimoTipo()).isEqualTo("BORRADOR_DISPONIBLE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void relojDeRondaEnRedis() {
        RedissonClient redis = mock(RedissonClient.class);
        RMap<Object, Object> mapa = mock(RMap.class);
        RSet<Object> set = mock(RSet.class);
        when(redis.getMap("fiesta-reloj:" + salaId)).thenReturn(mapa);
        when(redis.getSet("fiesta-salas-activas")).thenReturn(set);
        when(set.readAll()).thenReturn(Set.of(salaId.toString()));
        PartyRoundClock clock = new PartyRoundClock(redis);

        clock.iniciarRonda(salaId, 10_000);
        verify(mapa).put(eq("duracionMs"), eq("10000"));
        verify(set).add(salaId.toString());
        assertThat(clock.salasActivasIds()).containsExactly(salaId.toString());
        clock.terminarRonda(salaId);
        verify(set).remove(salaId.toString());

        assertThat(clock.msRestantes(salaId)).isZero();
        assertThat(clock.nuevaVentanaDeBorrador(salaId, 1000)).isFalse();

        long hace5s = Instant.now().toEpochMilli() - 5_000;
        when(mapa.get("inicioEpochMs")).thenReturn(String.valueOf(hace5s));
        when(mapa.get("duracionMs")).thenReturn("10000");
        assertThat(clock.msRestantes(salaId)).isBetween(1L, 5_000L);
        when(mapa.get("ultimaVentanaBorradorOfrecida")).thenReturn(null, "2", "9");
        assertThat(clock.nuevaVentanaDeBorrador(salaId, 2_000)).isTrue();
        assertThat(clock.nuevaVentanaDeBorrador(salaId, 2_000)).isFalse();
        assertThat(clock.nuevaVentanaDeBorrador(salaId, 60_000)).isFalse();
    }

    @Test
    void elTickAvisaCierraYAislaFallos() {
        SalaFiestaService salaService = mock(SalaFiestaService.class);
        PartyTickScheduler scheduler = new PartyTickScheduler(reloj, salaService, new PartyProperties());
        UUID terminada = UUID.randomUUID();
        UUID rota = UUID.randomUUID();
        UUID conError = UUID.randomUUID();
        when(reloj.salasActivasIds()).thenReturn(new java.util.LinkedHashSet<>(List.of(
                salaId.toString(), terminada.toString(), rota.toString(), conError.toString(), "no-uuid")));
        when(reloj.msRestantes(salaId)).thenReturn(5000L);
        when(reloj.nuevaVentanaDeBorrador(eq(salaId), anyLong())).thenReturn(true);
        when(reloj.msRestantes(terminada)).thenReturn(0L);
        when(reloj.msRestantes(rota)).thenThrow(new IllegalStateException("redis"));
        when(reloj.msRestantes(conError)).thenReturn(0L);
        doThrow(new IllegalStateException("db")).when(salaService).terminarRonda(conError);

        scheduler.tick();

        verify(salaService).notificarTick(salaId, 5000L);
        verify(salaService).notificarBorradorDisponible(salaId);
        verify(salaService).terminarRonda(terminada);
        verify(salaService).notificarFinDeRonda(terminada);
        verify(salaService, never()).notificarFinDeRonda(conError);
    }

    @Test
    void piezasDeLaPartida() {
        var estilo = new SeleccionadorPincelRonda().seleccionar();
        assertThat(estilo.grosor()).isBetween(6.0, 25.0);
        assertThat(estilo.opacidad()).isIn(0.5, 1.0);

        PartyProperties p = new PartyProperties();
        p.setTickIntervalMs(100);
        p.setEraserAppearsEverySeconds(5);
        p.setEraserHoldSeconds(3);
        p.setFreeMaxSalasActivas(2);
        assertThat(p.getTickIntervalMs()).isEqualTo(100);
        assertThat(p.getEraserAppearsEverySeconds()).isEqualTo(5);
        assertThat(p.getEraserHoldSeconds()).isEqualTo(3);
        assertThat(p.getFreeMaxSalasActivas()).isEqualTo(2);

        PartyRondaEstado r = new PartyRondaEstado(salaId, 2);
        assertThat(r.getSalaId()).isEqualTo(salaId);
        assertThat(r.getNumeroRonda()).isEqualTo(2);
        assertThat(r.siguienteSecuencia()).isEqualTo(1);
        assertThat(r.siguienteSecuencia()).isEqualTo(2);

        sala.iniciarNuevaRonda(TipoPincel.LAPIZ, "#000", 3, 1);
        assertThat(sala.getCodigo()).isEqualTo("ABC123");
        assertThat(sala.getCupoMaximo()).isEqualTo(2);
        sala.congelar();
        assertThat(sala.getEstado()).isNotEqualTo(EstadoSala.EN_RONDA);

        ParticipanteSala part = new ParticipanteSala(sala, anfitrion);
        assertThat(part.getSala()).isSameAs(sala);
        assertThat(part.getUsuario()).isSameAs(anfitrion);
    }
}
