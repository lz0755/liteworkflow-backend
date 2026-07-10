package com.liteworkflow.common.core.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.common.core.trace.TraceConstants;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesSubmittingContextAndRestoresWorkerContext() {
        MDC.put(TraceConstants.TRACE_ID, "request-trace");
        AtomicReference<String> captured = new AtomicReference<>();
        Runnable decorated = new MdcTaskDecorator().decorate(
                () -> captured.set(MDC.get(TraceConstants.TRACE_ID)));

        MDC.put(TraceConstants.TRACE_ID, "worker-trace");
        decorated.run();

        assertThat(captured).hasValue("request-trace");
        assertThat(MDC.get(TraceConstants.TRACE_ID)).isEqualTo("worker-trace");
    }
}
