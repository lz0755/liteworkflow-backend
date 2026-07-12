package com.liteworkflow.common.core.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        MDC.put(TraceConstants.EVENT_ID, "request-event");
        AtomicReference<String> captured = new AtomicReference<>();
        Runnable decorated = new MdcTaskDecorator().decorate(
                () -> captured.set(MDC.get(TraceConstants.TRACE_ID) + ":" + MDC.get(TraceConstants.EVENT_ID)));

        MDC.put(TraceConstants.TRACE_ID, "worker-trace");
        MDC.remove(TraceConstants.EVENT_ID);
        decorated.run();

        assertThat(captured).hasValue("request-trace:request-event");
        assertThat(MDC.get(TraceConstants.TRACE_ID)).isEqualTo("worker-trace");
        assertThat(MDC.get(TraceConstants.EVENT_ID)).isNull();
    }

    @Test
    void restoresWorkerContextWhenTaskFails() {
        MDC.put(TraceConstants.TRACE_ID, "request-trace");
        Runnable decorated = new MdcTaskDecorator().decorate(() -> {
            throw new IllegalStateException("expected failure");
        });
        MDC.put(TraceConstants.TRACE_ID, "worker-trace");

        assertThatThrownBy(decorated::run).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(TraceConstants.TRACE_ID)).isEqualTo("worker-trace");
    }
}
