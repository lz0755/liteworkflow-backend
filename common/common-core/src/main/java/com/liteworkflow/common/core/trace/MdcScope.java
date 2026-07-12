package com.liteworkflow.common.core.trace;

import java.util.Map;
import org.slf4j.MDC;

/**
 * Installs one unit-of-work MDC context and restores the calling thread exactly as it was.
 * Known work keys are cleared first so a reused worker cannot leak identifiers from an older job.
 */
public final class MdcScope implements AutoCloseable {

    private final Map<String, String> previousContext;
    private boolean closed;

    private MdcScope(Map<String, String> values) {
        previousContext = MDC.getCopyOfContextMap();
        TraceConstants.WORK_CONTEXT_KEYS.forEach(MDC::remove);
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) {
                    MDC.put(key, value);
                }
            });
        }
    }

    public static MdcScope open(Map<String, String> values) {
        return new MdcScope(values);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (previousContext == null || previousContext.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
        closed = true;
    }
}
