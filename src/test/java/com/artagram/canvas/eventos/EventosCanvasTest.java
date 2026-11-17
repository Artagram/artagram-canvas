package com.artagram.canvas.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.chat.ChatEventPublisher;
import com.artagram.canvas.chat.ComunidadChatStompController;
import com.artagram.canvas.chat.PersonalChatStompController;
import com.artagram.canvas.chat.dto.EnviarMensajeComunidadRequest;
import com.artagram.canvas.chat.dto.EnviarMensajePersonalRequest;
import com.artagram.canvas.chat.dto.MensajeComunidadDto;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.common.lock.DistributedLockService;
import com.artagram.canvas.community.ComunidadService;
import com.artagram.canvas.feed.FeedEventBridge;
import com.artagram.canvas.kpi.KpiEventPublisher;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.realtime.StompPrincipal;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionEventListener;
import com.artagram.canvas.usuario.UsuarioProyeccionRepository;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class EventosCanvasTest {

    private final UUID yo = UUID.randomUUID();
    private final UUID otro = UUID.randomUUID();
    private final StompPrincipal principal = new StompPrincipal(yo, "ana");

    @Test
    void puenteDelFeedRetransmiteYToleraPayloadsRotos() {
        RealtimeBroadcaster b = mock(RealtimeBroadcaster.class);
        FeedEventBridge bridge = new FeedEventBridge(b);
        UUID pub = UUID.randomUUID();
        bridge.onLikeUpdated(Map.of("publicacionId", pub.toString(), "contadorLikes", 3));
        bridge.onLikeUpdated(Map.of("publicacionId", "x"));
        Map<String, Object> com = new HashMap<>(Map.of("comentarioId", UUID.randomUUID().toString(),
                "publicacionId", pub.toString(), "autorId", yo.toString(), "timestamp", Instant.now().toString()));
        bridge.onCommentCreated(com);
        com.remove("timestamp");
        bridge.onCommentCreated(com);
        bridge.onCommentCreated(Map.of());
        bridge.onCommentDeleted(Map.of("publicacionId", pub.toString(), "comentarioId", UUID.randomUUID().toString()));
        bridge.onCommentDeleted(Map.of());
        verify(b).enviar(eq("/topic/publicaciones/" + pub + "/likes"), any());
        verify(b, times(3)).enviar(eq("/topic/publicaciones/" + pub + "/comentarios"), any());
    }

    @Test
    void proyeccionDeUsuariosSeSincroniza() {
        UsuarioProyeccionRepository repo = mock(UsuarioProyeccionRepository.class);
        UsuarioProyeccionEventListener l = new UsuarioProyeccionEventListener(repo);
        UsuarioProyeccion existente = new UsuarioProyeccion(yo, "viejo", false);
        when(repo.findById(yo)).thenReturn(Optional.of(existente));
        when(repo.findById(otro)).thenReturn(Optional.empty());

        l.onUserRegistered(Map.of("usuarioId", yo.toString(), "nombreArtista", "nuevo", "premium", true));
        assertThat(existente.getNombreArtista()).isEqualTo("nuevo");
        assertThat(existente.isPremium()).isTrue();
        l.onUserRegistered(Map.of("usuarioId", otro.toString()));
        verify(repo, times(2)).save(any());
        l.onUserRegistered(Map.of("usuarioId", "x"));

        l.onSubscriptionUpdated(Map.of("usuarioId", yo.toString(), "premium", false));
        assertThat(existente.isPremium()).isFalse();
        l.onSubscriptionUpdated(Map.of("usuarioId", otro.toString(), "premium", true));
        l.onSubscriptionUpdated(Map.of());
        l.onUserDeleted(Map.of("usuarioId", yo.toString()));
        l.onUserDeleted(Map.of());
        assertThat(existente.getCreadoEn()).isNotNull();

        UsuarioProyeccionResolver resolver = new UsuarioProyeccionResolver(repo);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(resolver.resolver(yo, "x")).isSameAs(existente);
        assertThat(resolver.resolver(new com.artagram.canvas.auth.JwtService.TokenPrincipal(otro, "sam", "USUARIO", true)).isPremium()).isTrue();
        when(repo.getReferenceById(yo)).thenReturn(existente);
        assertThat(resolver.referencia(yo)).isSameAs(existente);
    }

    @Test
    void publicadoresDeChatYKpiNoRompenSiRabbitFalla() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        ChatEventPublisher chat = new ChatEventPublisher(rabbit, "ex");
        KpiEventPublisher kpi = new KpiEventPublisher(rabbit, "ex");
        MensajeComunidadDto dto = new MensajeComunidadDto(UUID.randomUUID(), yo, "ana", "hola", Instant.now());
        EnviarMensajePersonalRequest personal = new EnviarMensajePersonalRequest(otro, "hey");
        chat.publicarMensajeComunidad(UUID.randomUUID(), dto);
        chat.publicarMensajePersonal(UUID.randomUUID(), yo, "ana", personal);
        kpi.publicarSesionIniciada(yo, "a1", "LIENZO", UUID.randomUUID());
        kpi.publicarSesionTerminada(yo, "a1", 1000);
        verify(rabbit, times(4)).convertAndSend(eq("ex"), anyString(), any(Object.class));

        doThrow(new IllegalStateException("caido")).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));
        chat.publicarMensajeComunidad(UUID.randomUUID(), dto);
        chat.publicarMensajePersonal(UUID.randomUUID(), yo, "ana", personal);
        kpi.publicarSesionIniciada(yo, "a1", "LIENZO", UUID.randomUUID());
        kpi.publicarSesionTerminada(yo, "a1", 1000);
    }

    @Test
    void controladoresStompDeChat() {
        RealtimeBroadcaster b = mock(RealtimeBroadcaster.class);
        ChatEventPublisher pub = mock(ChatEventPublisher.class);
        ComunidadService comunidades = mock(ComunidadService.class);
        UUID comunidadId = UUID.randomUUID();
        ComunidadChatStompController grupo = new ComunidadChatStompController(comunidades, b, pub);
        assertThatThrownBy(() -> grupo.enviar(comunidadId, new EnviarMensajeComunidadRequest("hola"), principal))
                .isInstanceOf(ApiException.class);
        when(comunidades.esMiembroActivo(yo, comunidadId)).thenReturn(true);
        grupo.enviar(comunidadId, new EnviarMensajeComunidadRequest("hola"), principal);
        verify(pub).publicarMensajeComunidad(eq(comunidadId), any());
        grupo.manejarError(new IllegalStateException("x"), principal);
        grupo.manejarError(new IllegalStateException(), principal);
        grupo.manejarError(new IllegalStateException(), null);

        PersonalChatStompController directo = new PersonalChatStompController(b, pub);
        assertThatThrownBy(() -> directo.enviar(new EnviarMensajePersonalRequest(yo, "yo"), principal))
                .isInstanceOf(ApiException.class);
        directo.enviar(new EnviarMensajePersonalRequest(otro, "hola"), principal);
        verify(b).enviarAUsuario(eq(otro.toString()), eq("/queue/mensajes"), any());
        directo.manejarError(new IllegalStateException("x"), principal);
        directo.manejarError(new IllegalStateException(), principal);
        directo.manejarError(new IllegalStateException(), null);
        verify(b, times(4)).enviarAUsuario(eq(yo.toString()), eq("/queue/errores"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void locksDistribuidosEnRedis() {
        RedissonClient redis = mock(RedissonClient.class);
        RBucket<Object> bucket = mock(RBucket.class);
        RScript script = mock(RScript.class);
        when(redis.getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(bucket);
        when(redis.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(bucket.trySet(eq("yo"), any(Long.class), eq(TimeUnit.MILLISECONDS))).thenReturn(true, false);
        when(bucket.get()).thenReturn("yo");
        when(script.eval(any(RScript.Mode.class), anyString(), any(RScript.ReturnType.class), org.mockito.ArgumentMatchers.<java.util.List<Object>>any(), any(Object[].class)))
                .thenReturn(1L, 0L, 1L, null);
        DistributedLockService locks = new DistributedLockService(redis);

        assertThat(locks.adquirir("k", "yo", Duration.ofSeconds(1))).isTrue();
        assertThat(locks.adquirir("k", "yo", Duration.ofSeconds(1))).isFalse();
        assertThat(locks.renovar("k", "yo", Duration.ofSeconds(1))).isTrue();
        assertThat(locks.renovar("k", "yo", Duration.ofSeconds(1))).isFalse();
        assertThat(locks.liberar("k", "yo")).isTrue();
        assertThat(locks.liberar("k", "yo")).isFalse();
        assertThat(locks.propietarioActual("k")).contains("yo");
        verify(redis, never()).getLock(anyString());
    }
}
