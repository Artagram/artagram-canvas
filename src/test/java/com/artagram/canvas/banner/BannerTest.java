package com.artagram.canvas.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.banner.dto.PintarRegionRequest;
import com.artagram.canvas.canvas.CanvasProperties;
import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.common.lock.ConcurrencyMetrics;
import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.community.dto.ComunidadResponse;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.realtime.StompPrincipal;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class BannerTest {

    private final UUID comunidadId = UUID.randomUUID();
    private final UUID yo = UUID.randomUUID();
    private final UsuarioProyeccion ana = new UsuarioProyeccion(yo, "ana", false);
    private final List<Punto> linea = List.of(new Punto(0.1, 0.1), new Punto(0.5, 0.5));

    private BannerComunitarioRepository banners;
    private BannerRegionEstadoRepository regiones;
    private BannerTrazoRepository trazos;
    private BannerSnapshotRepository snapshots;
    private ComunidadService comunidades;
    private BannerRateLimiter limiter;
    private BannerSnapshotStorageService storage;
    private BannerService service;
    private CanvasProperties props;
    private BannerComunitario banner;

    @BeforeEach
    void setUp() {
        banners = mock(BannerComunitarioRepository.class);
        regiones = mock(BannerRegionEstadoRepository.class);
        trazos = mock(BannerTrazoRepository.class);
        snapshots = mock(BannerSnapshotRepository.class);
        comunidades = mock(ComunidadService.class);
        limiter = mock(BannerRateLimiter.class);
        storage = mock(BannerSnapshotStorageService.class);
        UsuarioProyeccionResolver resolver = mock(UsuarioProyeccionResolver.class);
        when(resolver.resolver(yo, "ana")).thenReturn(ana);
        props = new CanvasProperties();
        props.setBannerSize(200);
        props.setBannerRegionSize(100);
        service = new BannerService(banners, regiones, trazos, snapshots, resolver, comunidades, limiter,
                new ConcurrencyMetrics(new SimpleMeterRegistry()), props, storage);
        banner = new BannerComunitario(comunidadId, 200, 100);
        when(banners.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(regiones.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PintarRegionRequest pintar(int region) {
        return new PintarRegionRequest(region, TipoPincel.LAPIZ, "#ff0000", 2, 1, linea);
    }

    private ComunidadResponse comunidad(boolean publica) {
        return new ComunidadResponse(comunidadId, "c", "", publica, "ana", 1, null);
    }

    @Test
    void pintarAprovisionaElBannerYRespetaPermisosYRitmo() {
        when(banners.findByComunidadId(comunidadId)).thenReturn(Optional.empty());
        when(comunidades.ver(comunidadId, yo)).thenReturn(comunidad(true));
        when(limiter.permitir(comunidadId, yo)).thenReturn(true);
        when(regiones.buscarParaActualizar(any(), eq(1))).thenReturn(Optional.empty());

        var evento = service.pintar(comunidadId, yo, "ana", null, pintar(1));
        assertThat(evento.secuencia()).isEqualTo(1L);
        verify(comunidades).unirse(yo, "ana", comunidadId);
        verify(regiones, times(5)).save(any());

        when(banners.findByComunidadId(comunidadId)).thenReturn(Optional.of(banner));
        assertThatThrownBy(() -> service.pintar(comunidadId, yo, "ana", null, pintar(4)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("REGION_INVALIDA");

        when(comunidades.ver(comunidadId, yo)).thenReturn(comunidad(false));
        assertThatThrownBy(() -> service.pintar(comunidadId, yo, "ana", "USUARIO", pintar(0)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NO_ERES_MIEMBRO");
        BannerRegionEstado r0 = new BannerRegionEstado(banner.getId(), 0);
        when(regiones.buscarParaActualizar(banner.getId(), 0)).thenReturn(Optional.of(r0));
        assertThat(service.pintar(comunidadId, yo, "ana", "admin", pintar(0)).regionIndex()).isZero();

        when(comunidades.esMiembroActivo(yo, comunidadId)).thenReturn(true);
        when(limiter.permitir(comunidadId, yo)).thenReturn(false);
        assertThatThrownBy(() -> service.pintar(comunidadId, yo, "ana", null, pintar(0)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("LIMITE_DE_RITMO");
        assertThat(r0.getContadorSecuencia()).isEqualTo(1);
        assertThat(r0.getBannerId()).isEqualTo(banner.getId());
        assertThat(r0.getId()).isNotNull();
    }

    @Test
    void estadoCombinaSnapshotYTrazosPendientes() {
        when(banners.findByComunidadId(comunidadId)).thenReturn(Optional.of(banner));
        BannerSnapshot snap = new BannerSnapshot(banner.getId(), 0, new byte[]{1, 2}, 3);
        snap.setS3Key("banners/x/0.png");
        BannerSnapshot sinS3 = new BannerSnapshot(banner.getId(), 1, new byte[]{3}, 0);
        sinS3.setS3Key(" ");
        when(snapshots.findByBannerIdAndRegionIndex(banner.getId(), 0)).thenReturn(Optional.of(snap));
        when(snapshots.findByBannerIdAndRegionIndex(banner.getId(), 1)).thenReturn(Optional.of(sinS3));
        when(snapshots.findByBannerIdAndRegionIndex(banner.getId(), 2)).thenReturn(Optional.empty());
        when(snapshots.findByBannerIdAndRegionIndex(banner.getId(), 3)).thenReturn(Optional.empty());
        when(storage.presignarUrl("banners/x/0.png")).thenReturn("https://s3/x");
        BannerTrazo t = new BannerTrazo(banner.getId(), 0, 4, ana, TipoPincel.PLUMA, "#000000", 1, 1, linea);
        when(trazos.findByBannerIdAndRegionIndexAndSecuenciaGreaterThanOrderBySecuenciaAsc(banner.getId(), 0, 3))
                .thenReturn(List.of(t));

        var estado = service.obtenerEstado(comunidadId);
        assertThat(estado).hasSize(4);
        assertThat(estado.get(0).snapshotPngUrl()).isEqualTo("https://s3/x");
        assertThat(estado.get(0).trazosDesdeSnapshot()).singleElement().extracting("autorNombre").isEqualTo("ana");
        assertThat(estado.get(1).snapshotPngUrl()).isNull();
        assertThat(estado.get(1).snapshotPngBase64()).isEqualTo("Aw==");
        assertThat(estado.get(2).snapshotPngBase64()).isNull();
        assertThat(t.getBannerId()).isEqualTo(banner.getId());
        assertThat(banner.getTamano()).isEqualTo(200);
        assertThat(snap.getRegionIndex()).isZero();
        assertThat(snap.getBannerId()).isEqualTo(banner.getId());
    }

    @Test
    void elJobConsolidaSoloRegionesConSuficientesTrazos() {
        BannerRasterService raster = mock(BannerRasterService.class);
        BannerSnapshotJob job = new BannerSnapshotJob(banners, regiones, trazos, snapshots, raster, storage);
        BannerRegionEstado r0 = new BannerRegionEstado(banner.getId(), 0);
        BannerRegionEstado r1 = new BannerRegionEstado(banner.getId(), 1);
        BannerRegionEstado r2 = new BannerRegionEstado(banner.getId(), 2);
        when(banners.findAll()).thenReturn(List.of(banner));
        when(regiones.findByBannerId(banner.getId())).thenReturn(List.of(r0, r1, r2));
        BannerSnapshot previo = new BannerSnapshot(banner.getId(), 1, new byte[]{9}, 5);
        when(snapshots.findByBannerIdAndRegionIndex(banner.getId(), 1)).thenReturn(Optional.of(previo));
        when(snapshots.findByBannerIdAndRegionIndex(banner.getId(), 0)).thenReturn(Optional.empty());
        when(snapshots.findByBannerIdAndRegionIndex(banner.getId(), 2)).thenReturn(Optional.empty());
        when(trazos.countByBannerIdAndRegionIndexAndSecuenciaGreaterThan(banner.getId(), 0, 0)).thenReturn(25L);
        when(trazos.countByBannerIdAndRegionIndexAndSecuenciaGreaterThan(banner.getId(), 1, 5)).thenReturn(30L);
        when(trazos.countByBannerIdAndRegionIndexAndSecuenciaGreaterThan(banner.getId(), 2, 0)).thenReturn(3L);
        List<BannerTrazo> lote = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            lote.add(new BannerTrazo(banner.getId(), 0, i, ana, TipoPincel.LAPIZ, "#000000", 1, 1, linea));
        }
        when(trazos.findByBannerIdAndRegionIndexAndSecuenciaGreaterThanOrderBySecuenciaAsc(eq(banner.getId()), anyInt(), anyLong()))
                .thenReturn(lote);
        when(raster.renderizar(any(), anyInt(), any())).thenReturn(new byte[]{7});
        when(snapshots.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storage.claveDe(any(), anyInt())).thenReturn("k");
        when(storage.subir("k", new byte[]{7})).thenReturn(true, false);

        job.consolidar();

        verify(snapshots, times(1)).save(any());
        assertThat(previo.getUltimaSecuenciaIncluida()).isEqualTo(25);
        assertThat(previo.getPng()).containsExactly(7);
        verify(raster, never()).renderizar(any(), anyInt(), eq(List.of()));
    }

    @Test
    void rasterizaLineasPuntosYBases() {
        BannerRasterService raster = new BannerRasterService();
        byte[] blanco = raster.lienzoEnBlanco(10);
        assertThat(blanco).isNotEmpty();
        BannerTrazo linea8 = new BannerTrazo(UUID.randomUUID(), 0, 1, ana, TipoPincel.LAPIZ, "#ff000080", 5, 2, linea);
        BannerTrazo punto = new BannerTrazo(UUID.randomUUID(), 0, 2, ana, TipoPincel.LAPIZ, "#00ff00", 0.1, -1, List.of(new Punto(0.5, 0.5)));
        assertThat(raster.renderizar(null, 20, List.of(linea8, punto))).isNotEmpty();
        assertThat(raster.renderizar(blanco, 10, List.of(punto))).isNotEmpty();
        assertThat(raster.renderizar(new byte[]{1, 2, 3}, 10, List.of())).isNotEmpty();
    }

    @Test
    void limitadorDeRitmoConDegradacionSuave() {
        RedissonClient redis = mock(RedissonClient.class);
        RRateLimiter rl = mock(RRateLimiter.class);
        when(redis.getRateLimiter(org.mockito.ArgumentMatchers.anyString())).thenReturn(rl);
        when(rl.tryAcquire()).thenReturn(true, false);
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        long[] ahora = {10_000};
        BannerRateLimiter l = new BannerRateLimiter(redis, props, registry, () -> ahora[0]);
        assertThat(l.permitir(comunidadId, yo)).isTrue();
        when(rl.tryAcquire()).thenReturn(false);
        // Un ataque: varios excesos en el mismo segundo y otro despues.
        for (int i = 0; i < 5; i++) {
            assertThat(l.permitir(comunidadId, yo)).isFalse();
        }
        ahora[0] += 1500;
        assertThat(l.permitir(comunidadId, yo)).isFalse();
        assertThat(registry.get("banner_eventos_descartados_total").counter().count()).isEqualTo(6.0);
        assertThat(new BannerRateLimiter(redis, props, registry)).isNotNull();
        when(redis.getRateLimiter(org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException("redis caido"));
        assertThat(l.permitir(comunidadId, yo)).isTrue();
    }

    @Test
    void autorizadorStompYControlador() {
        BannerTopicAuthorizer auth = new BannerTopicAuthorizer(comunidades);
        StompPrincipal p = new StompPrincipal(yo, "ana");
        String destino = "/topic/banner/" + comunidadId;
        assertThat(auth.soporta(destino)).isTrue();
        assertThat(auth.autorizado(p, "/topic/x")).isFalse();
        when(comunidades.ver(comunidadId, yo)).thenReturn(comunidad(false), comunidad(true));
        assertThat(auth.autorizado(p, destino)).isFalse();
        assertThat(auth.autorizado(p, destino)).isTrue();

        RealtimeBroadcaster broadcaster = mock(RealtimeBroadcaster.class);
        BannerService svc = mock(BannerService.class);
        BannerStompController stomp = new BannerStompController(svc, broadcaster);
        stomp.pintar(comunidadId, pintar(0), p);
        verify(broadcaster).enviar(eq("/topic/banner/" + comunidadId), any());
        stomp.manejarError(new IllegalStateException("x"), p);
        stomp.manejarError(new IllegalStateException(), p);
        stomp.manejarError(new IllegalStateException(), null);
        verify(broadcaster, times(2)).enviarAUsuario(eq(yo.toString()), eq("/queue/errores"), any());
    }

    @Test
    void almacenamientoS3ToleraFallos() {
        ServidorQueRechaza s3Caido = new ServidorQueRechaza();
        BannerSnapshotStorageService s = new BannerSnapshotStorageService(s3Caido.url(), "us-east-1", "b", "ak", "sk", true);
        s3Caido.close();
        assertThat(s.claveDe(comunidadId, 3)).isEqualTo("banners/" + comunidadId + "/3.png");
        assertThat(s.presignarUrl("banners/a.png")).contains("banners/a.png");

        S3Client s3 = mock(S3Client.class);
        ReflectionTestUtils.setField(s, "s3Client", s3);
        assertThat(s.subir("k", new byte[]{1})).isTrue();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(new IllegalStateException("x"));
        assertThat(s.subir("k", new byte[]{1})).isFalse();

        when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(NoSuchBucketException.builder().message("no").build());
        ReflectionTestUtils.invokeMethod(s, "asegurarBucket");
        verify(s3).createBucket(any(CreateBucketRequest.class));
        when(s3.createBucket(any(CreateBucketRequest.class))).thenThrow(new IllegalStateException("x"));
        ReflectionTestUtils.invokeMethod(s, "asegurarBucket");

        ReflectionTestUtils.setField(s, "s3Presigner", null);
        assertThat(s.presignarUrl("k")).isNull();

        BannerSnapshotStorageService sinCreds = new BannerSnapshotStorageService("", "us-east-1", "b", "", "", false);
        assertThat(sinCreds.claveDe(comunidadId, 0)).endsWith("0.png");
    }
}
