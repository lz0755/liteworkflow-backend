package com.liteworkflow.common.core.trace;

public final class TraceConstants {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REACTOR_CONTEXT_KEY = TraceConstants.class.getName() + ".traceId";

    private TraceConstants() {
    }
}
