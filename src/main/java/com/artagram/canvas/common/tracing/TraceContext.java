package com.artagram.canvas.common.tracing;

/**
 * Claves de MDC compartidas entre el filtro HTTP y el interceptor STOMP, para que
 * un mismo trazo/mensaje deje logs correlacionados en ambos transportes.
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String USER_ID_MDC_KEY = "usuarioId";
    public static final String OPERATION_MDC_KEY = "operacion";

    private TraceContext() {
    }
}
