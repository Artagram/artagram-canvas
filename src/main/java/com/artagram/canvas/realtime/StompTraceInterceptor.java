package com.artagram.canvas.realtime;

import com.artagram.canvas.common.tracing.TraceContext;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Propaga el traceId tambien en el canal STOMP: justo donde el rastro se suele
 * perder al saltar de REST a WebSocket. Si el cliente manda un header nativo
 * "traceId" (ej. el mismo que trae de una llamada REST previa) se reusa; si no,
 * se genera uno nuevo para ese mensaje.
 * <p>
 * {@code preSend} corre en el hilo del WebSocket y el controlador en un hilo
 * del executor: por eso el traceId viaja como cabecera del mensaje y el MDC se
 * llena en {@code beforeHandle}, que si corre en el hilo del controlador.
 */
@Component
public class StompTraceInterceptor implements ExecutorChannelInterceptor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    static final String HEADER_TRACE_ID = "artagramTraceId";

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String traceId = primerHeader(accessor, "traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MessageHeaderAccessor mutable = MessageHeaderAccessor.getMutableAccessor(message);
        mutable.setHeader(HEADER_TRACE_ID, traceId);
        return MessageBuilder.createMessage(message.getPayload(), mutable.getMessageHeaders());
    }

    @Override
    public Message<?> beforeHandle(@NonNull Message<?> message, @NonNull MessageChannel channel,
                                   @NonNull MessageHandler handler) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        Object traceId = message.getHeaders().get(HEADER_TRACE_ID);
        if (traceId != null) {
            MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId.toString());
        }
        MDC.put(TraceContext.OPERATION_MDC_KEY, String.valueOf(accessor.getDestination()));
        if (accessor.getUser() instanceof StompPrincipal principal) {
            MDC.put(TraceContext.USER_ID_MDC_KEY, principal.usuarioId().toString());
        }
        return message;
    }

    @Override
    public void afterMessageHandled(@NonNull Message<?> message, @NonNull MessageChannel channel,
                                     @NonNull MessageHandler handler, Exception ex) {
        MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        MDC.remove(TraceContext.OPERATION_MDC_KEY);
        MDC.remove(TraceContext.USER_ID_MDC_KEY);
    }

    private String primerHeader(StompHeaderAccessor accessor, String nombre) {
        var valores = accessor.getNativeHeader(nombre);
        return (valores == null || valores.isEmpty()) ? null : valores.get(0);
    }
}
