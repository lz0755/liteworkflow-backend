package com.liteworkflow.common.core.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcScopeTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void clearsStaleWorkKeysAndRestoresTheWorkerContext() {
        MDC.put(TraceConstants.TRACE_ID, "stale-trace");
        MDC.put(TraceConstants.EVENT_ID, "stale-event");
        MDC.put("staticKey", "keep-me");

        try (MdcScope ignored = MdcScope.open(Map.of(
                TraceConstants.TRACE_ID, "new-trace",
                TraceConstants.PROJECT_ID, "new-project"))) {
            assertThat(MDC.get(TraceConstants.TRACE_ID)).isEqualTo("new-trace");
            assertThat(MDC.get(TraceConstants.EVENT_ID)).isNull();
            assertThat(MDC.get(TraceConstants.PROJECT_ID)).isEqualTo("new-project");
            assertThat(MDC.get("staticKey")).isEqualTo("keep-me");
        }

        assertThat(MDC.get(TraceConstants.TRACE_ID)).isEqualTo("stale-trace");
        assertThat(MDC.get(TraceConstants.EVENT_ID)).isEqualTo("stale-event");
        assertThat(MDC.get(TraceConstants.PROJECT_ID)).isNull();
        assertThat(MDC.get("staticKey")).isEqualTo("keep-me");
    }
}
