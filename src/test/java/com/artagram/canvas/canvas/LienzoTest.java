package com.artagram.canvas.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.canvas.dto.EditarTrazoRequest;
import com.artagram.canvas.canvas.dto.EnviarPuntosRequest;
import com.artagram.canvas.canvas.dto.TrazoDto;
import com.artagram.canvas.canvas.dto.TrazoEventoDto;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.common.lock.ConcurrencyMetrics;
import com.artagram.canvas.common.lock.DistributedLockService;
import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.realtime.StompPrincipal;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LienzoTest {

    private final UUID comunidadId = UUID.randomUUID();
    private final UUID yo = UUID.randomUUID();
    private final String propietario = yo + "::ana";
    private final List<Punto> puntos = List.of(new Punto(1, 1));
    private final UsuarioProyeccion ana = new UsuarioProyeccion(yo, "ana", false);

    private LienzoRepository lienzos;
    private TrazoRepository trazos;
    private UsuarioProyeccionResolver resolver;
    private ComunidadService comunidades;
    private RealtimeBroadcaster broadcaster;
    private DistributedLockService locks;
    private CanvasService canvas;
    private TrazoLockService lockService;

    @BeforeEach
    void setUp() {
        lienzos = mock(LienzoRepository.class);
        trazos = mock(TrazoRepository.class);
        resolver = mock(UsuarioProyeccionResolver.class);
        comunidades = mock(ComunidadService.class);
        broadcaster = mock(RealtimeBroadcaster.class);
        locks = mock(DistributedLockService.class);
        var registry = new SimpleMeterRegistry();
        canvas = new CanvasService(lienzos, trazos, resolver, comunidades, new CanvasMetrics(registry));
        lockService = new TrazoLockService(locks, trazos, comunidades, broadcaster, new ConcurrencyMetrics(registry));
        when(comunidades.esMiembroActivo(yo, comunidadId)).thenReturn(true);
        when(resolver.resolver(yo, "ana")).thenReturn(ana);
    }

    private EnviarPuntosRequest puntos() {
        return new EnviarPuntosRequest(UUID.randomUUID(), TipoPincel.LAPIZ, "#000000", 2, 1, 0, puntos);
    }

    @Test
    void parcialYFinalCreanElLienzoYNumeranLosTrazos() {
        assertThat(canvas.procesarParcial(comunidadId, yo, "ana", puntos()).tipo()).isEqualTo("PARCIAL");
        when(lienzos.buscarParaActualizar(comunidadId)).thenReturn(Optional.empty());
        when(lienzos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TrazoEventoDto fin = canvas.finalizarTrazo(comunidadId, yo, "ana", puntos());
        assertThat(fin.tipo()).isEqualTo("FINAL");
        assertThat(fin.secuencia()).isEqualTo(1L);
        verify(comunidades, times(1)).esMiembroActivo(yo, comunidadId);
        canvas.registrarLatencia(5);

        UUID intruso = UUID.randomUUID();
        assertThatThrownBy(() -> canvas.procesarParcial(comunidadId, intruso, "x", puntos())).isInstanceOf(ApiException.class);

        Lienzo lienzo = new Lienzo(comunidadId);
        assertThat(lienzo.getComunidadId()).isEqualTo(comunidadId);
        assertThat(lienzo.getContadorSecuencia()).isZero();
        when(lienzos.findByComunidadId(comunidadId)).thenReturn(Optional.of(lienzo));
        Trazo t = new Trazo(lienzo.getId(), ana, 3, TipoPincel.PLUMA, "#fff", 1, 1, 2, puntos);
        when(trazos.findByLienzoIdAndSecuenciaGreaterThanOrderBySecuenciaAsc(lienzo.getId(), 0)).thenReturn(List.of(t));
        assertThat(canvas.obtenerTrazosDesde(comunidadId, 0)).containsExactly(t);
        assertThat(canvas.obtenerTrazosDesde(UUID.randomUUID(), 0)).isEmpty();

        TrazoDto dto = TrazoDto.de(t);
        assertThat(dto.autorNombre()).isEqualTo("ana");
        assertThat(dto.capa()).isEqualTo(2);
        assertThat(t.getLienzoId()).isEqualTo(lienzo.getId());
        assertThat(t.getCreadoEn()).isNotNull();
    }

    @Test
    void elBloqueoEsReentranteYSeNiegaAOtros() {
        UUID trazoId = UUID.randomUUID();
        String clave = "trazo-lock:" + trazoId;
        when(locks.adquirir(eq(clave), eq(propietario), any())).thenReturn(true, false, false);
        when(locks.propietarioActual(clave)).thenReturn(Optional.of(propietario), Optional.of(UUID.randomUUID() + "::sam"),
                Optional.of(UUID.randomUUID() + "::sam"), Optional.empty(), Optional.empty());
        when(locks.renovar(eq(clave), eq(propietario), any())).thenReturn(true);

        assertThat(lockService.tomar(comunidadId, trazoId, yo, "ana").tomado()).isTrue();
        assertThat(lockService.tomar(comunidadId, trazoId, yo, "ana").tomado()).isTrue();
        var negado = lockService.tomar(comunidadId, trazoId, yo, "ana");
        assertThat(negado.tomado()).isFalse();
        assertThat(negado.propietarioNombre()).isEqualTo("sam");
        assertThat(lockService.tomar(comunidadId, trazoId, yo, "ana").propietarioNombre()).isEqualTo("alguien");
        assertThatThrownBy(() -> lockService.tomar(comunidadId, trazoId, UUID.randomUUID(), "x")).isInstanceOf(ApiException.class);

        assertThat(lockService.renovar(trazoId, yo, "ana")).isTrue();
        when(locks.liberar(clave, propietario)).thenReturn(true, false);
        lockService.liberar(comunidadId, trazoId, yo, "ana");
        lockService.liberar(comunidadId, trazoId, yo, "ana");
        verify(broadcaster, times(1)).enviar(eq("/topic/lienzo/" + comunidadId + "/bloqueos"),
                eq(new com.artagram.canvas.canvas.dto.BloqueoEventoDto("LIBERADO", trazoId, null)));
    }

    @Test
    void editarExigeElBloqueo() {
        UUID trazoId = UUID.randomUUID();
        String clave = "trazo-lock:" + trazoId;
        EditarTrazoRequest req = new EditarTrazoRequest(TipoPincel.MARCADOR, "#123456", 4, 0.5, 1, puntos);
        when(locks.propietarioActual(clave)).thenReturn(Optional.empty(), Optional.of("otro"), Optional.of(propietario));

        assertThatThrownBy(() -> lockService.editar(comunidadId, trazoId, yo, "ana", req)).isInstanceOf(ApiException.class)
                .hasMessage("No tienes tomado este trazo");
        assertThatThrownBy(() -> lockService.editar(comunidadId, trazoId, yo, "ana", req)).isInstanceOf(ApiException.class)
                .hasMessageContaining("lo tiene otro");
        assertThatThrownBy(() -> lockService.editar(comunidadId, trazoId, yo, "ana", req)).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("TRAZO_NO_ENCONTRADO");

        Trazo t = new Trazo(UUID.randomUUID(), ana, 1, TipoPincel.LAPIZ, "#000", 1, 1, 0, puntos);
        when(trazos.findById(trazoId)).thenReturn(Optional.of(t));
        when(locks.propietarioActual(clave)).thenReturn(Optional.of(propietario));
        lockService.editar(comunidadId, trazoId, yo, "ana", req);
        assertThat(t.getTipoPincel()).isEqualTo(TipoPincel.MARCADOR);
        assertThat(t.getOpacidad()).isEqualTo(0.5);
        assertThat(t.getGrosor()).isEqualTo(4);
        assertThat(t.getColorHex()).isEqualTo("#123456");
        assertThat(t.getPuntos()).isEqualTo(puntos);
    }

    @Test
    void controladorStompDelLienzo() {
        CanvasService svc = mock(CanvasService.class);
        CanvasStompController stomp = new CanvasStompController(svc, broadcaster);
        StompPrincipal p = new StompPrincipal(yo, "ana");
        stomp.recibirParcial(comunidadId, puntos(), p);
        stomp.recibirFinal(comunidadId, puntos(), p);
        verify(svc).registrarLatencia(anyLong());
        stomp.manejarError(new IllegalStateException("x"), p);
        stomp.manejarError(new IllegalStateException(), p);
        stomp.manejarError(new IllegalStateException("x"), null);
        verify(broadcaster, times(2)).enviarAUsuario(eq(yo.toString()), eq("/queue/errores"), any());
        verify(broadcaster, never()).enviarAUsuario(anyString(), eq("/otra"), any());
    }

    @Test
    void conversorDePuntosYPropiedades() {
        PuntosConverter conv = new PuntosConverter();
        String json = conv.convertToDatabaseColumn(puntos);
        assertThat(conv.convertToEntityAttribute(json)).isEqualTo(puntos);
        assertThatThrownBy(() -> conv.convertToEntityAttribute("no-json")).isInstanceOf(IllegalStateException.class);

        CanvasProperties p = new CanvasProperties();
        p.setPointBatchMs(10);
        p.setStrokeLockTtlSeconds(5);
        p.setBannerSize(200);
        p.setBannerRegionSize(50);
        p.setBannerRateLimitPerSecond(9);
        assertThat(p.getPointBatchMs()).isEqualTo(10);
        assertThat(p.getStrokeLockTtlSeconds()).isEqualTo(5);
        assertThat(p.getBannerSize()).isEqualTo(200);
        assertThat(p.getBannerRegionSize()).isEqualTo(50);
        assertThat(p.getBannerRateLimitPerSecond()).isEqualTo(9);
        assertThat(p.regionsPerSide()).isEqualTo(4);
    }
}
