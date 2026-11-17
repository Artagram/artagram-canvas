package com.artagram.canvas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artagram.canvas.auth.JwtService.TokenPrincipal;
import com.artagram.canvas.auth.JwtUserResolver;
import com.artagram.canvas.banner.BannerController;
import com.artagram.canvas.banner.BannerService;
import com.artagram.canvas.canvas.CanvasController;
import com.artagram.canvas.canvas.CanvasService;
import com.artagram.canvas.canvas.Punto;
import com.artagram.canvas.canvas.TipoPincel;
import com.artagram.canvas.canvas.Trazo;
import com.artagram.canvas.canvas.TrazoLockController;
import com.artagram.canvas.canvas.TrazoLockService;
import com.artagram.canvas.canvas.dto.BloqueoTrazoResponse;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.common.error.GlobalExceptionHandler;
import com.artagram.canvas.common.lock.DistributedLockService;
import com.artagram.canvas.community.ComunidadController;
import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.community.dto.ComunidadResponse;
import com.artagram.canvas.party.ParticipanteSalaRepository;
import com.artagram.canvas.party.PartyBorradorController;
import com.artagram.canvas.party.PartyChatStompController;
import com.artagram.canvas.party.PartyDrawService;
import com.artagram.canvas.party.PartyHerramientaController;
import com.artagram.canvas.party.PartyStompController;
import com.artagram.canvas.party.PartyTopicAuthorizer;
import com.artagram.canvas.party.SalaFiestaController;
import com.artagram.canvas.party.SalaFiestaService;
import com.artagram.canvas.party.dto.BloqueoBorradorResponse;
import com.artagram.canvas.party.dto.BloqueoHerramientaResponse;
import com.artagram.canvas.party.dto.EnviarMensajeChatRequest;
import com.artagram.canvas.party.dto.TrazarEnRondaRequest;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.realtime.StompPrincipal;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ControladoresCanvasTest {

    private final UUID yo = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final TokenPrincipal principal = new TokenPrincipal(yo, "ana", "USUARIO", false);
    private final String punto = "[{\"x\":0.1,\"y\":0.2}]";

    private JwtUserResolver resolver;
    private SalaFiestaService salas;
    private PartyDrawService draw;
    private DistributedLockService locks;
    private ComunidadService comunidades;
    private CanvasService canvas;
    private TrazoLockService trazoLocks;
    private BannerService banner;
    private RealtimeBroadcaster broadcaster;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        resolver = mock(JwtUserResolver.class);
        salas = mock(SalaFiestaService.class);
        draw = mock(PartyDrawService.class);
        locks = mock(DistributedLockService.class);
        comunidades = mock(ComunidadService.class);
        canvas = mock(CanvasService.class);
        trazoLocks = mock(TrazoLockService.class);
        banner = mock(BannerService.class);
        broadcaster = mock(RealtimeBroadcaster.class);
        when(resolver.resolverRequerido(any())).thenReturn(principal);
        when(resolver.resolverOpcional(any())).thenReturn(Optional.of(principal));
        mvc = MockMvcBuilders.standaloneSetup(
                        new SalaFiestaController(salas, locks, resolver),
                        new PartyBorradorController(draw, resolver),
                        new PartyHerramientaController(draw, resolver),
                        new ComunidadController(comunidades, resolver),
                        new CanvasController(canvas, comunidades, resolver),
                        new TrazoLockController(trazoLocks, resolver),
                        new BannerController(banner, comunidades, broadcaster, resolver))
                .setControllerAdvice(new GlobalExceptionHandler(new SimpleMeterRegistry()))
                .build();
    }

    private ComunidadResponse comunidad(boolean publica) {
        return new ComunidadResponse(id, "c", "", publica, "ana", 1, null);
    }

    @Test
    void salasDeFiesta() throws Exception {
        mvc.perform(post("/api/fiestas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"duracionRondaSegundos\":30,\"cupoMaximo\":4,\"totalRondas\":2}")).andExpect(status().isCreated());
        mvc.perform(post("/api/fiestas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"duracionRondaSegundos\":1,\"cupoMaximo\":4}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"));
        mvc.perform(post("/api/fiestas/unirse").param("codigo", "ABC")).andExpect(status().isOk());
        mvc.perform(post("/api/fiestas/unirse")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_FALTANTE"));
        mvc.perform(post("/api/fiestas/" + id + "/rondas")).andExpect(status().isOk());
        mvc.perform(get("/api/fiestas/activas")).andExpect(status().isOk());
        mvc.perform(delete("/api/fiestas/" + id)).andExpect(status().isNoContent());
        mvc.perform(post("/api/fiestas/" + id + "/salir")).andExpect(status().isNoContent());
        when(salas.limpiarSalasInactivas(yo)).thenReturn(2);
        mvc.perform(post("/api/fiestas/limpiar")).andExpect(jsonPath("$.cerradas").value(2));

        mvc.perform(get("/api/fiestas/" + id)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fiestas/" + id + "/estado")).andExpect(status().isForbidden());
        when(salas.esParticipanteActivo(yo, id)).thenReturn(true);
        mvc.perform(get("/api/fiestas/" + id)).andExpect(status().isOk());
        when(locks.propietarioActual("fiesta-borrador:" + id)).thenReturn(Optional.of(yo + "::ana"));
        mvc.perform(get("/api/fiestas/" + id + "/estado")).andExpect(status().isOk());
        verify(salas).obtenerEstado(id, "ana");
        when(locks.propietarioActual("fiesta-borrador:" + id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/fiestas/" + id + "/estado")).andExpect(status().isOk());
    }

    @Test
    void borradorYHerramientas() throws Exception {
        when(draw.tomarBorrador(id, yo, "ana")).thenReturn(new BloqueoBorradorResponse(true, "ana"), new BloqueoBorradorResponse(false, "sam"));
        mvc.perform(post("/api/fiestas/" + id + "/borrador")).andExpect(status().isOk());
        mvc.perform(post("/api/fiestas/" + id + "/borrador")).andExpect(status().isConflict());
        mvc.perform(delete("/api/fiestas/" + id + "/borrador")).andExpect(status().isNoContent());
        when(draw.tomarHerramienta(id, yo, "ana", "LAPIZ")).thenReturn(new BloqueoHerramientaResponse(true, "LAPIZ", "ana"),
                new BloqueoHerramientaResponse(false, "LAPIZ", "sam"));
        mvc.perform(post("/api/fiestas/" + id + "/herramientas/LAPIZ")).andExpect(status().isOk());
        mvc.perform(post("/api/fiestas/" + id + "/herramientas/LAPIZ")).andExpect(status().isConflict());
        mvc.perform(delete("/api/fiestas/" + id + "/herramientas/LAPIZ")).andExpect(status().isNoContent());
        mvc.perform(get("/api/fiestas/" + id + "/herramientas")).andExpect(status().isOk());
    }

    @Test
    void comunidades() throws Exception {
        mvc.perform(post("/api/comunidades").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Acuarela\",\"publica\":true}")).andExpect(status().isCreated());
        mvc.perform(post("/api/comunidades/" + id + "/miembros")).andExpect(status().isOk());
        mvc.perform(get("/api/comunidades/" + id + "/miembros")).andExpect(status().isOk());
        mvc.perform(post("/api/comunidades/" + id + "/miembros/invitar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreArtista\":\"sam\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/comunidades/mias")).andExpect(status().isOk());
        mvc.perform(get("/api/comunidades/recomendadas")).andExpect(status().isOk());
        mvc.perform(get("/api/comunidades/" + id)).andExpect(status().isOk());
        mvc.perform(patch("/api/comunidades/" + id + "/miembros/" + yo + "/rol").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"MODERADOR\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/comunidades/" + id + "/miembros/" + yo)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/comunidades/" + id)).andExpect(status().isNoContent());
        when(resolver.resolverOpcional(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/comunidades/" + id)).andExpect(status().isOk());
        verify(comunidades).ver(id, null);
    }

    @Test
    void lienzoBloqueosYBanner() throws Exception {
        mvc.perform(get("/api/comunidades/" + id + "/lienzo/trazos")).andExpect(status().isForbidden());
        when(comunidades.esMiembroActivo(yo, id)).thenReturn(true);
        Trazo t = new Trazo(UUID.randomUUID(), new UsuarioProyeccion(yo, "ana", false), 1, TipoPincel.LAPIZ, "#000000",
                1, 1, 0, List.of(new Punto(0, 0)));
        when(canvas.obtenerTrazosDesde(id, 0)).thenReturn(List.of(t));
        mvc.perform(get("/api/comunidades/" + id + "/lienzo/trazos")).andExpect(jsonPath("$[0].autorNombre").value("ana"));

        String base = "/api/comunidades/" + id + "/lienzo/trazos/" + yo;
        when(trazoLocks.tomar(id, yo, yo, "ana")).thenReturn(new BloqueoTrazoResponse(true, "ana"), new BloqueoTrazoResponse(false, "sam"));
        mvc.perform(post(base + "/bloqueo")).andExpect(status().isOk());
        mvc.perform(post(base + "/bloqueo")).andExpect(status().isConflict());
        when(trazoLocks.renovar(yo, yo, "ana")).thenReturn(true, false);
        mvc.perform(put(base + "/bloqueo")).andExpect(status().isOk());
        mvc.perform(put(base + "/bloqueo")).andExpect(status().isGone());
        mvc.perform(delete(base + "/bloqueo")).andExpect(status().isNoContent());
        mvc.perform(put(base).contentType(MediaType.APPLICATION_JSON).content(
                "{\"tipoPincel\":\"LAPIZ\",\"colorHex\":\"#000000\",\"grosor\":2,\"opacidad\":1,\"capa\":0,\"puntos\":" + punto + "}"))
                .andExpect(status().isOk());
        mvc.perform(put(base).contentType(MediaType.APPLICATION_JSON).content("{no json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUERPO_INVALIDO"));

        String bannerUrl = "/api/comunidades/" + id + "/banner";
        mvc.perform(get(bannerUrl)).andExpect(status().isOk());
        when(comunidades.esMiembroActivo(yo, id)).thenReturn(false);
        when(comunidades.ver(id, yo)).thenReturn(comunidad(true), comunidad(false));
        mvc.perform(get(bannerUrl)).andExpect(status().isOk());
        mvc.perform(get(bannerUrl)).andExpect(status().isForbidden());
        when(resolver.resolverRequerido(any())).thenReturn(new TokenPrincipal(yo, "ana", "ADMIN", false));
        when(comunidades.ver(id, yo)).thenReturn(comunidad(false));
        mvc.perform(get(bannerUrl)).andExpect(status().isOk());
        mvc.perform(post(bannerUrl + "/pintar").contentType(MediaType.APPLICATION_JSON).content(
                "{\"regionIndex\":0,\"tipoPincel\":\"LAPIZ\",\"colorHex\":\"#000000\",\"grosor\":2,\"opacidad\":1,\"puntos\":" + punto + "}"))
                .andExpect(status().isOk());
        verify(broadcaster).enviar(eq("/topic/banner/" + id), any());
    }

    @Test
    void erroresGlobales() throws Exception {
        org.mockito.Mockito.doThrow(new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "CAIDO", "x")).when(salas).listarSalasActivasDelUsuario(yo);
        mvc.perform(get("/api/fiestas/activas")).andExpect(status().isServiceUnavailable());
        org.mockito.Mockito.doThrow(new org.springframework.dao.OptimisticLockingFailureException("v")).when(salas).listarSalasActivasDelUsuario(yo);
        mvc.perform(get("/api/fiestas/activas")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICTO_CONCURRENCIA"));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(salas).listarSalasActivasDelUsuario(yo);
        mvc.perform(get("/api/fiestas/activas")).andExpect(status().isInternalServerError());
        mvc.perform(put("/api/fiestas/activas")).andExpect(status().isMethodNotAllowed());
        var handler = new GlobalExceptionHandler(new SimpleMeterRegistry());
        assertThat(handler.handleRecursoNoEncontrado(new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/x")).getStatusCode().value()).isEqualTo(404);
        assertThat(ApiException.tooManyRequests("A", "b").getStatus().value()).isEqualTo(429);
        assertThat(ApiException.unauthorized("A", "b").getStatus().value()).isEqualTo(401);
    }

    @Test
    void stompYAutorizadorDeFiesta() {
        StompPrincipal p = new StompPrincipal(yo, "ana");
        TrazarEnRondaRequest req = new TrazarEnRondaRequest(null, null, null, null, List.of(new Punto(0, 0)));
        PartyStompController stomp = new PartyStompController(draw, broadcaster);
        stomp.trazar(id, req, p);
        stomp.parcial(id, req, p);
        stomp.borrar(id, req, p);
        verify(draw).borrar(id, yo, "ana", req);
        stomp.manejarError(new IllegalStateException("x"), p);
        stomp.manejarError(new IllegalStateException(), p);
        stomp.manejarError(new IllegalStateException(), null);

        ParticipanteSalaRepository participantes = mock(ParticipanteSalaRepository.class);
        PartyChatStompController chat = new PartyChatStompController(participantes, broadcaster);
        assertThatThrownBy(() -> chat.enviar(id, new EnviarMensajeChatRequest("hola"), p)).isInstanceOf(ApiException.class);
        when(participantes.existsBySala_IdAndUsuario_UsuarioIdAndActivoTrue(id, yo)).thenReturn(true);
        chat.enviar(id, new EnviarMensajeChatRequest("hola"), p);
        verify(broadcaster).enviar(eq("/topic/fiesta/" + id + "/chat"), any());
        chat.manejarError(new IllegalStateException("x"), p);
        chat.manejarError(new IllegalStateException(), p);
        chat.manejarError(new IllegalStateException(), null);
        verify(broadcaster, times(4)).enviarAUsuario(eq(yo.toString()), eq("/queue/errores"), any());

        PartyTopicAuthorizer auth = new PartyTopicAuthorizer(participantes);
        assertThat(auth.soporta("/topic/fiesta/" + id)).isTrue();
        assertThat(auth.soporta("/topic/otra")).isFalse();
        assertThat(auth.autorizado(p, "/topic/otra")).isFalse();
        assertThat(auth.autorizado(p, "/app/fiesta/" + id + "/trazo")).isTrue();
        assertThat(auth.autorizado(new StompPrincipal(UUID.randomUUID(), "x"), "/topic/fiesta/" + id)).isFalse();
        verify(broadcaster, org.mockito.Mockito.never()).enviar(anyString(), eq("nunca"));
    }
}
