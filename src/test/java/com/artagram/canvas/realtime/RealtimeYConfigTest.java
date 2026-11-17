package com.artagram.canvas.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.auth.JwtService;
import com.artagram.canvas.common.config.CorsProperties;
import com.artagram.canvas.common.config.WebConfig;
import com.artagram.canvas.common.tracing.TraceIdFilter;
import com.artagram.canvas.feed.FeedEventsRabbitMqConfig;
import com.artagram.canvas.usuario.CanvasUserEventsRabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.slf4j.MDC;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

class RealtimeYConfigTest {

    private final UUID yo = UUID.randomUUID();
    private final StompPrincipal principal = new StompPrincipal(yo, "ana");

    private Message<byte[]> stomp(StompCommand cmd, String destino, StompPrincipal user, String traceId) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(cmd);
        if (destino != null) {
            acc.setDestination(destino);
        }
        acc.setUser(user);
        if (traceId != null) {
            acc.addNativeHeader("traceId", traceId);
        }
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void broadcasterRelayaPorRedisYEntregaLocal() {
        RedissonClient redis = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(redis.getTopic(anyString())).thenReturn(topic);
        SimpMessagingTemplate stompTemplate = mock(SimpMessagingTemplate.class);
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        RealtimeBroadcaster b = new RealtimeBroadcaster(redis, stompTemplate, new ObjectMapper(), registry);
        b.suscribirse();
        ArgumentCaptor<MessageListener> listener = ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(any(Class.class), listener.capture());
        when(topic.publish(any())).thenAnswer(inv -> {
            listener.getValue().onMessage("canal", inv.getArgument(0));
            return 1L;
        });

        b.enviar("/topic/x", Map.of("a", 1));
        verify(stompTemplate).convertAndSend(eq("/topic/x"), any(Object.class));
        b.enviarAUsuario("u1", "/queue/y", List.of(1));
        verify(stompTemplate).convertAndSendToUser(eq("u1"), eq("/queue/y"), any(Object.class));
        doThrow(new IllegalStateException("ws")).when(stompTemplate).convertAndSend(eq("/topic/z"), any(Object.class));
        b.enviar("/topic/z", "x");
        assertThatThrownBy(() -> b.enviar("/topic/x", new Object())).isInstanceOf(IllegalArgumentException.class);

        // Cada entrega mide cuanto tardo en atravesar Redis, por canal.
        assertThat(registry.get("trazo_propagacion").tag("canal", "x").timer().count()).isEqualTo(1);
        assertThat(registry.get("trazo_propagacion").tag("canal", "usuario").timer().count()).isEqualTo(1);
        MDC.put("traceId", "traza-relay");
        b.enviar("/topic/lienzo/c1", Map.of("a", 1));
        MDC.clear();
        assertThat(registry.get("trazo_propagacion").tag("canal", "lienzo").timer().count()).isEqualTo(1);
    }

    @Test
    void elCanalDeLaMetricaTienePocosValores() {
        assertThat(RealtimeBroadcaster.canal("/topic/lienzo/123", false)).isEqualTo("lienzo");
        assertThat(RealtimeBroadcaster.canal("/topic/banner/9", false)).isEqualTo("banner");
        assertThat(RealtimeBroadcaster.canal("/queue/x", false)).isEqualTo("otro");
        assertThat(RealtimeBroadcaster.canal(null, false)).isEqualTo("otro");
        assertThat(RealtimeBroadcaster.canal("/queue/x", true)).isEqualTo("usuario");
    }

    @Test
    void interceptorDeAutorizacionStomp() {
        DestinationAuthorizer auth = mock(DestinationAuthorizer.class);
        when(auth.soporta(anyString())).thenAnswer(inv -> ((String) inv.getArgument(0)).startsWith("/topic/privado"));
        when(auth.autorizado(principal, "/topic/privado/ok")).thenReturn(true);
        StompAuthChannelInterceptor i = new StompAuthChannelInterceptor(List.of(auth));
        MessageChannel canal = mock(MessageChannel.class);

        assertThat(i.preSend(stomp(StompCommand.CONNECT, null, null, null), canal)).isNotNull();
        assertThat(i.preSend(stomp(StompCommand.SUBSCRIBE, null, principal, null), canal)).isNotNull();
        assertThat(i.preSend(stomp(StompCommand.SUBSCRIBE, "/user/queue/x", null, null), canal)).isNotNull();
        assertThatThrownBy(() -> i.preSend(stomp(StompCommand.SEND, "/app/x", null, null), canal))
                .isInstanceOf(StompAuthException.class);
        assertThat(i.preSend(stomp(StompCommand.SEND, "/app/libre", principal, null), canal)).isNotNull();
        assertThat(i.preSend(stomp(StompCommand.SUBSCRIBE, "/topic/privado/ok", principal, null), canal)).isNotNull();
        assertThatThrownBy(() -> i.preSend(stomp(StompCommand.SUBSCRIBE, "/topic/privado/no", principal, null), canal))
                .isInstanceOf(StompAuthException.class);
    }

    @Test
    void interceptorDeTrazasStomp() {
        StompTraceInterceptor t = new StompTraceInterceptor();
        MessageChannel canal = mock(MessageChannel.class);
        assertThat(t.getOrder()).isEqualTo(Integer.MIN_VALUE);
        MessageHandler handler = m -> { };
        Message<?> conTraza = t.preSend(stomp(StompCommand.SEND, "/app/x", principal, "traza-1"), canal);
        // preSend no toca el MDC del hilo del WebSocket: solo deja el id en el mensaje.
        assertThat(MDC.get("traceId")).isNull();
        t.beforeHandle(conTraza, canal, handler);
        assertThat(MDC.get("traceId")).isEqualTo("traza-1");
        assertThat(MDC.get("usuarioId")).isEqualTo(yo.toString());
        t.afterMessageHandled(conTraza, canal, handler, null);
        assertThat(MDC.get("traceId")).isNull();
        Message<?> sinTraza = t.preSend(stomp(StompCommand.SEND, "/app/x", null, " "), canal);
        t.beforeHandle(sinTraza, canal, handler);
        assertThat(MDC.get("traceId")).hasSize(16);
        t.beforeHandle(stomp(StompCommand.SEND, "/app/x", null, null), canal, handler);
        MDC.clear();
    }

    @Test
    void handshakeConTokenEnQueryOEncabezado() {
        JwtService jwt = mock(JwtService.class);
        var p = new JwtService.TokenPrincipal(yo, "ana", "USUARIO", false);
        when(jwt.validar("bueno")).thenReturn(Optional.of(p));
        when(jwt.validar("malo")).thenReturn(Optional.empty());
        JwtHandshakeInterceptor i = new JwtHandshakeInterceptor(jwt);
        WebSocketHandler ws = mock(WebSocketHandler.class);
        ServerHttpResponse resp = mock(ServerHttpResponse.class);

        ServerHttpRequest query = mock(ServerHttpRequest.class);
        when(query.getURI()).thenReturn(URI.create("http://x/ws?token=bueno"));
        Map<String, Object> attrs = new HashMap<>();
        assertThat(i.beforeHandshake(query, resp, ws, attrs)).isTrue();
        java.security.Principal handshake = new JwtHandshakeHandlerProbe().determinar(query, ws, attrs);
        assertThat(handshake).isEqualTo(new StompPrincipal(yo, "ana"));
        assertThat(new JwtHandshakeHandlerProbe().determinar(query, ws, Map.of())).isNull();

        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/ws");
        servlet.addHeader("Authorization", "Bearer malo");
        assertThat(i.beforeHandshake(new ServletServerHttpRequest(servlet), resp, ws, new HashMap<>())).isFalse();
        MockHttpServletRequest sinHeader = new MockHttpServletRequest("GET", "/ws");
        assertThat(i.beforeHandshake(new ServletServerHttpRequest(sinHeader), resp, ws, new HashMap<>())).isFalse();
        MockHttpServletRequest basic = new MockHttpServletRequest("GET", "/ws");
        basic.addHeader("Authorization", "Basic x");
        assertThat(i.beforeHandshake(new ServletServerHttpRequest(basic), resp, ws, new HashMap<>())).isFalse();
        ServerHttpRequest sinToken = mock(ServerHttpRequest.class);
        when(sinToken.getURI()).thenReturn(URI.create("http://x/ws"));
        assertThat(i.beforeHandshake(sinToken, resp, ws, new HashMap<>())).isFalse();
        verify(resp, org.mockito.Mockito.atLeast(3)).setStatusCode(HttpStatus.UNAUTHORIZED);
        i.afterHandshake(query, resp, ws, null);
    }


    static class JwtHandshakeHandlerProbe extends JwtHandshakeHandler {
        java.security.Principal determinar(ServerHttpRequest r, WebSocketHandler h, Map<String, Object> a) {
            return determineUser(r, h, a);
        }
    }

    @Test
    void configuracionWebSocketCorsYRabbit() {
        CorsProperties cors = new CorsProperties();
        cors.setAllowedOrigins(List.of("https://artagram.app"));
        assertThat(cors.getAllowedOrigins()).containsExactly("https://artagram.app");
        WebSocketConfig cfg = new WebSocketConfig(cors, new StompAuthChannelInterceptor(List.of()),
                new StompTraceInterceptor(), mock(JwtService.class));
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration reg = mock(StompWebSocketEndpointRegistration.class, RETURNS_SELF);
        when(registry.addEndpoint("/ws")).thenReturn(reg);
        cfg.registerStompEndpoints(registry);
        verify(reg).setAllowedOriginPatterns("https://artagram.app");
        cfg.configureMessageBroker(new MessageBrokerRegistry(mock(SubscribableChannel.class), mock(MessageChannel.class)));
        cfg.configureClientInboundChannel(new ChannelRegistration());
        cfg.configureClientOutboundChannel(new ChannelRegistration());

        CorsRegistry corsRegistry = new CorsRegistry();
        new WebConfig(cors).addCorsMappings(corsRegistry);
        Map<String, org.springframework.web.cors.CorsConfiguration> mapeos =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(corsRegistry, "getCorsConfigurations");
        assertThat(mapeos.get("/**").getAllowedOriginPatterns()).containsExactly("https://artagram.app");
        assertThat(mapeos.get("/**").getAllowCredentials()).isTrue();

        CanvasUserEventsRabbitMqConfig u = new CanvasUserEventsRabbitMqConfig();
        TopicExchange ex = u.eventsExchange();
        assertThat(u.jsonMessageConverter()).isNotNull();
        assertThat(u.canvasUserRegisteredBinding(u.canvasUserRegisteredQueue(), ex).getRoutingKey()).isEqualTo("user.registered");
        assertThat(u.canvasUserDeletedBinding(u.canvasUserDeletedQueue(), ex).getRoutingKey()).isEqualTo("user.deleted");
        assertThat(u.canvasSubscriptionUpdatedBinding(u.canvasSubscriptionUpdatedQueue(), ex).getRoutingKey())
                .isEqualTo("subscription.updated");
        FeedEventsRabbitMqConfig f = new FeedEventsRabbitMqConfig();
        assertThat(f.canvasLikeUpdatedBinding(f.canvasLikeUpdatedQueue(), ex).getRoutingKey()).isEqualTo("like.updated");
        assertThat(f.canvasCommentCreatedBinding(f.canvasCommentCreatedQueue(), ex).getRoutingKey()).isEqualTo("comment.created");
        assertThat(f.canvasCommentDeletedBinding(f.canvasCommentDeletedQueue(), ex).getRoutingKey()).isEqualTo("comment.deleted");
    }

    @Test
    void filtroDeTrazaHttp() throws Exception {
        TraceIdFilter filtro = new TraceIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
        req.addHeader("X-Trace-Id", "abc");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filtro.doFilter(req, resp, new MockFilterChain());
        assertThat(resp.getHeader("X-Trace-Id")).isEqualTo("abc");
        MockHttpServletResponse otra = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest("GET", "/api/y"), otra, new MockFilterChain());
        assertThat(otra.getHeader("X-Trace-Id")).isNotBlank();
        MockHttpServletRequest blanco = new MockHttpServletRequest("GET", "/api/z");
        blanco.addHeader("X-Trace-Id", " ");
        filtro.doFilter(blanco, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(MDC.get("traceId")).isNull();
    }
}
