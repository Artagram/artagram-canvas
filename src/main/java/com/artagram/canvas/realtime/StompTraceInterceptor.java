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
import org.springframework.stereotype.Component;

/**
 * Propaga el traceId tambien en el canal STOMP: justo donde el rastro se suele
 * perder al saltar de REST a WebSocket. Si el cliente manda un header nativo
 * "traceId" (ej. el mismo que trae de una llamada REST previa) se reusa; si no,
 * se genera uno nuevo para ese mensaje.
 */
@Component
public class StompTraceInterceptor implements ExecutorChannelInterceptor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String traceId = primerHeader(accessor, "traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);
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
