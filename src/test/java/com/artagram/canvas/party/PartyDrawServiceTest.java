package com.artagram.canvas.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.common.lock.ConcurrencyMetrics;
import com.artagram.canvas.common.lock.DistributedLockService;
import com.artagram.canvas.party.dto.PartyEventoDto;
import com.artagram.canvas.party.dto.TrazarEnRondaRequest;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PartyDrawServiceTest {

    private final UUID salaId = UUID.randomUUID();
    private final UUID yo = UUID.randomUUID();
    private final String propietario = yo + "::ana";
    private final List<Punto> puntos = List.of(new Punto(1, 2), new Punto(3, 4));

    private SalaFiestaRepository salas;
    private ParticipanteSalaRepository participantes;
    private PartyTrazoRepository trazos;
    private UsuarioProyeccionResolver usuarios;
    private DistributedLockService locks;
    private RealtimeBroadcaster broadcaster;
    private PartyDrawService service;
    private SalaFiesta sala;

    @BeforeEach
    void setUp() {
        salas = mock(SalaFiestaRepository.class);
        participantes = mock(ParticipanteSalaRepository.class);
        trazos = mock(PartyTrazoRepository.class);
        usuarios = mock(UsuarioProyeccionResolver.class);
        locks = mock(DistributedLockService.class);
        broadcaster = mock(RealtimeBroadcaster.class);
        service = new PartyDrawService(salas, participantes, trazos, usuarios, locks,
                new ConcurrencyMetrics(new SimpleMeterRegistry()), broadcaster);
        sala = new SalaFiesta("ABC123", new UsuarioProyeccion(yo, "ana", false), 60, 8, 3);
        when(salas.findById(salaId)).thenReturn(Optional.of(sala));
        when(participantes.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(salaId, yo)).thenReturn(true);
        when(trazos.findBySalaIdOrderBySecuenciaAsc(salaId)).thenReturn(List.of());
        when(usuarios.referencia(any())).thenAnswer(inv -> new UsuarioProyeccion(inv.getArgument(0), "x", false));
    }

    private PartyEventoDto ultimoEvento() {
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster, org.mockito.Mockito.atLeastOnce()).enviar(eq("/topic/fiesta/" + salaId), evento.capture());
        return (PartyEventoDto) evento.getValue();
    }

    @Test
    void trazarUsaElEstiloDeLaRondaYNumeraEnOrden() {
        service.trazar(salaId, yo, "ana", new TrazarEnRondaRequest(null, null, null, null, puntos));
        sala.iniciarNuevaRonda(TipoPincel.ACUARELA, "#123456", 8.0, 0.5);
        service.trazar(salaId, yo, "ana", new TrazarEnRondaRequest(TipoPincel.PLUMA, "#ffffff", 3.0, 0.9, puntos));
        service.trazar(salaId, yo, "ana", new TrazarEnRondaRequest(null, null, null, null, puntos));

        var lista = service.obtenerTrazos(salaId, 1);
        assertThat(lista).extracting("secuencia").containsExactly(1L, 2L, 3L);
        assertThat(lista.get(0).grosor()).isEqualTo(20.0);
        assertThat(lista.get(1).tipoPincel()).isEqualTo(TipoPincel.PLUMA);
        assertThat(lista.get(2).colorHex()).isEqualTo("#123456");
        assertThat(lista.get(2).opacidad()).isEqualTo(0.5);
        assertThat(ultimoEvento().tipo()).isEqualTo("TRAZO");
    }

    @Test
    void quienNoParticipaNoDibujaYElCacheEvitaConsultas() {
        UUID intruso = UUID.randomUUID();
        service.trazar(salaId, intruso, "x", new TrazarEnRondaRequest(null, null, null, null, puntos));
        service.parcial(salaId, intruso, "x", new TrazarEnRondaRequest(null, null, null, null, puntos));
        verify(broadcaster, never()).enviar(anyString(), any());

        service.parcial(salaId, yo, "ana", new TrazarEnRondaRequest(null, null, 2.0, 1.0, puntos));
        assertThat(ultimoEvento().trazo().borrador()).isTrue();
        service.parcial(salaId, yo, "ana", new TrazarEnRondaRequest(TipoPincel.LAPIZ, "#000", 2.0, 1.0, puntos));
        assertThat(ultimoEvento().tipo()).isEqualTo("TRAZO_PARCIAL");
        verify(participantes, times(1)).existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(salaId, yo);

        UUID otraSala = UUID.randomUUID();
        when(salas.findById(otraSala)).thenReturn(Optional.empty());
        when(participantes.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(otraSala, yo)).thenReturn(true);
        assertThatThrownBy(() -> service.trazar(otraSala, yo, "ana", new TrazarEnRondaRequest(null, null, null, null, puntos)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void elBorradorSeDisputaConUnLock() {
        when(locks.adquirir(eq("fiesta-borrador:" + salaId), eq(propietario), any())).thenReturn(true, false);
        when(locks.propietarioActual("fiesta-borrador:" + salaId)).thenReturn(Optional.of(UUID.randomUUID() + "::sam"), Optional.empty());

        assertThat(service.tomarBorrador(salaId, yo, "ana").tomado()).isTrue();
        var negado = service.tomarBorrador(salaId, yo, "ana");
        assertThat(negado.tomado()).isFalse();
        assertThat(negado.propietarioNombre()).isEqualTo("sam");
        when(locks.adquirir(anyString(), anyString(), any())).thenReturn(false);
        assertThat(service.tomarBorrador(salaId, yo, "ana").propietarioNombre()).isEqualTo("alguien");
        assertThatThrownBy(() -> service.tomarBorrador(salaId, UUID.randomUUID(), "x")).isInstanceOf(ApiException.class);
    }

    @Test
    void borrarExigeTenerElBorrador() {
        when(locks.propietarioActual("fiesta-herramienta:" + salaId + ":BORRADOR")).thenReturn(Optional.empty());
        when(locks.propietarioActual("fiesta-borrador:" + salaId)).thenReturn(Optional.empty(), Optional.of(propietario));

        assertThatThrownBy(() -> service.borrar(salaId, yo, "ana", new TrazarEnRondaRequest(null, null, null, null, puntos)))
                .isInstanceOf(ApiException.class);
        service.borrar(salaId, yo, "ana", new TrazarEnRondaRequest(null, null, null, null, puntos));
        assertThat(ultimoEvento().trazo().grosor()).isEqualTo(40.0);

        when(locks.propietarioActual("fiesta-herramienta:" + salaId + ":BORRADOR")).thenReturn(Optional.of(propietario));
        service.borrar(salaId, yo, "ana", new TrazarEnRondaRequest(null, null, 12.0, null, puntos));
        assertThat(ultimoEvento().trazo().grosor()).isEqualTo(12.0);
    }

    @Test
    void consolidarMueveElBufferALaBaseYMezclaConLoGuardado() {
        UsuarioProyeccion autor = new UsuarioProyeccion(yo, "ana", false);
        PartyTrazo guardado = new PartyTrazo(salaId, 1, 5, autor, false, TipoPincel.LAPIZ, "#000", 2, 1, puntos);
        when(trazos.findBySalaIdOrderBySecuenciaAsc(salaId)).thenReturn(List.of(guardado));

        assertThat(service.obtenerTrazos(salaId, 1)).singleElement().extracting("secuencia").isEqualTo(5L);
        service.trazar(salaId, yo, "ana", new TrazarEnRondaRequest(null, null, null, null, puntos));
        assertThat(service.obtenerTrazos(salaId, 1)).extracting("secuencia").containsExactly(5L, 6L);

        service.consolidarTrazosEnBaseDeDatos(salaId);
        verify(trazos).saveAll(any());
        service.consolidarTrazosEnBaseDeDatos(salaId);
        verify(trazos, times(1)).saveAll(any());

        UUID sinSala = UUID.randomUUID();
        when(participantes.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(sinSala, yo)).thenReturn(true);
        when(salas.findById(sinSala)).thenReturn(Optional.of(sala), Optional.empty());
        when(trazos.findBySalaIdOrderBySecuenciaAsc(sinSala)).thenReturn(List.of());
        service.trazar(sinSala, yo, "ana", new TrazarEnRondaRequest(null, null, null, null, puntos));
        service.consolidarTrazosEnBaseDeDatos(sinSala);
        verify(trazos, times(1)).saveAll(any());
    }

    @Test
    void herramientasExclusivas() {
        String claveBorrador = "fiesta-herramienta:" + salaId + ":BORRADOR";
        String claveLapiz = "fiesta-herramienta:" + salaId + ":LAPIZ";
        when(locks.adquirir(eq(claveBorrador), eq(propietario), any())).thenReturn(true);
        when(locks.adquirir(eq(claveLapiz), eq(propietario), any())).thenReturn(false);
        when(locks.propietarioActual(claveLapiz)).thenReturn(Optional.of("sin-separador"));
        when(locks.propietarioActual(claveBorrador)).thenReturn(Optional.of(propietario));
        when(locks.liberar(anyString(), eq(propietario))).thenReturn(true, false, true, true);

        assertThat(service.tomarHerramienta(salaId, yo, "ana", "borrador").herramienta()).isEqualTo("BORRADOR");
        verify(locks).adquirir(eq("fiesta-borrador:" + salaId), eq(propietario), any());
        var lapiz = service.tomarHerramienta(salaId, yo, "ana", "lapiz");
        assertThat(lapiz.tomado()).isFalse();
        assertThat(lapiz.propietarioNombre()).isEqualTo("sin-separador");
        when(locks.propietarioActual(claveLapiz)).thenReturn(Optional.empty());
        assertThat(service.tomarHerramienta(salaId, yo, "ana", "lapiz").propietarioNombre()).isEqualTo("alguien");
        assertThatThrownBy(() -> service.tomarHerramienta(salaId, UUID.randomUUID(), "x", "lapiz")).isInstanceOf(ApiException.class);

        assertThat(service.consultarHerramientas(salaId)).containsEntry("BORRADOR", "ana").hasSize(1);
        service.liberarHerramienta(salaId, yo, "ana", "borrador");
        service.liberarHerramienta(salaId, yo, "ana", "lapiz");
        service.liberarBorrador(salaId, yo, "ana");
        assertThat(ultimoEvento().tipo()).isIn("BORRADOR_LIBERADO", "HERRAMIENTA_LIBERADA");
    }
}
