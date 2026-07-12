package com.liteworkflow.common.core.trace;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

public final class TraceIds {

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private TraceIds() {
    }

    public static String resolve(String candidate) {
        String safeCandidate = safeOrNull(candidate);
        if (safeCandidate != null) {
            return safeCandidate;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String safeOrNull(String candidate) {
        return candidate != null && SAFE_TRACE_ID.matcher(candidate).matches() ? candidate : null;
    }

    public static String current() {
        return MDC.get(TraceConstants.TRACE_ID);
    }
}
