package com.artagram.canvas.realtime;

import com.artagram.canvas.auth.JwtService;
import com.artagram.canvas.common.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Broker simple en memoria: solo entrega a las sesiones WebSocket conectadas
 * a ESTA instancia. Correr mas de una instancia detras de un load balancer no
 * rompe el tiempo real gracias a {@link RealtimeBroadcaster}, que reparte cada
 * mensaje a todas las instancias via Redis Pub/Sub antes de que cada una lo
 * entregue local con este mismo broker — la config de acá no cambia, el relay
 * vive por encima.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Hilos para procesar lo que mandan los clientes y para entregarles mensajes. */
    static final int HILOS_ENTRADA = 8;
    static final int HILOS_SALIDA = 8;

    private final CorsProperties corsProperties;
    private final StompAuthChannelInterceptor authInterceptor;
    private final StompTraceInterceptor traceInterceptor;
    private final JwtService jwtService;

    public WebSocketConfig(CorsProperties corsProperties, StompAuthChannelInterceptor authInterceptor,
                            StompTraceInterceptor traceInterceptor, JwtService jwtService) {
        this.corsProperties = corsProperties;
        this.authInterceptor = authInterceptor;
        this.traceInterceptor = traceInterceptor;
        this.jwtService = jwtService;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Los mensajes de una misma sesion se procesan en el orden en que llegaron
        // aunque el canal de entrada tenga varios hilos (un "final" nunca se
        // adelanta a los "parcial" de su trazo).
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new JwtHandshakeHandler())
                .addInterceptors(new JwtHandshakeInterceptor(jwtService))
                .setAllowedOriginPatterns(corsProperties.getAllowedOrigins().toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // "/queue" (no "/user") va junto a "/topic": los destinos privados que resuelve
        // UserDestinationMessageHandler terminan como /queue/mensajes-user<sesion>, y esa
        // reescritura necesita caer dentro de un prefijo que el broker sepa enrutar.
        registry.enableSimpleBroker("/topic", "/queue");
        // Orden por sesion en la salida, para poder usar varios hilos (ver abajo).
        registry.setPreservePublishOrder(true);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(traceInterceptor, authInterceptor);
        registration.taskExecutor().corePoolSize(HILOS_ENTRADA).maxPoolSize(HILOS_ENTRADA);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Antes era un solo hilo para no desordenar los mensajes de una sesion, y
        // ese hilo se volvia el cuello de botella con muchos trazos por segundo
        // (escenario 1). Ahora el orden por sesion lo garantiza
        // setPreservePublishOrder y la entrega se reparte entre varios hilos.
        registration.taskExecutor().corePoolSize(HILOS_SALIDA).maxPoolSize(HILOS_SALIDA);
    }
}
