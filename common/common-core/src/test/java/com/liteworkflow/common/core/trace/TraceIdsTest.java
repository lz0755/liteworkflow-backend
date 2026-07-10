package com.liteworkflow.common.core.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceIdsTest {

    @Test
    void keepsSafeTraceIdAndReplacesLogInjectionInput() {
        assertThat(TraceIds.resolve("client.trace-123")).isEqualTo("client.trace-123");
        assertThat(TraceIds.resolve("bad\ntrace")).matches("[a-f0-9]{32}");
    }
}
