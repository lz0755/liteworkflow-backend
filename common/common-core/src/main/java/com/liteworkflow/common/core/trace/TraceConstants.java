package com.liteworkflow.common.core.trace;

import java.util.Set;

public final class TraceConstants {

    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String EVENT_ID = "eventId";
    public static final String WORKSPACE_ID = "workspaceId";
    public static final String PROJECT_ID = "projectId";
    public static final String REQUEST_METHOD = "requestMethod";
    public static final String REQUEST_PATH = "requestPath";
    public static final String STATUS = "status";
    public static final String LATENCY_MS = "latencyMs";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REACTOR_CONTEXT_KEY = TraceConstants.class.getName() + ".traceId";

    /** Keys owned by a single request, message, or background unit of work. */
    public static final Set<String> WORK_CONTEXT_KEYS = Set.of(
            TRACE_ID,
            USER_ID,
            EVENT_ID,
            WORKSPACE_ID,
            PROJECT_ID,
            REQUEST_METHOD,
            REQUEST_PATH,
            STATUS,
            LATENCY_MS);

    private TraceConstants() {
    }
}
