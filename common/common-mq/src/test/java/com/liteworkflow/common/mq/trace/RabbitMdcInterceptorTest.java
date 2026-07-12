package com.liteworkflow.common.mq.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.mq.event.EventHeaders;
import java.util.UUID;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageBuilder;

class RabbitMdcInterceptorTest {

    private final RabbitMdcInterceptor interceptor = new RabbitMdcInterceptor();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void installsMessageHeadersAndRestoresAReusedConsumerThread() throws Throwable {
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        var message = MessageBuilder.withBody(new byte[0])
                .setHeader(EventHeaders.TRACE_ID, "message-trace")
                .setHeader(EventHeaders.EVENT_ID, eventId.toString())
                .setHeader(EventHeaders.WORKSPACE_ID, workspaceId.toString())
                .build();
        MDC.put(TraceConstants.TRACE_ID, "worker-trace");
        MDC.put(TraceConstants.PROJECT_ID, "stale-project");
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[] {message});
        when(invocation.proceed()).thenAnswer(ignored -> {
            assertThat(MDC.get(TraceConstants.TRACE_ID)).isEqualTo("message-trace");
            assertThat(MDC.get(TraceConstants.EVENT_ID)).isEqualTo(eventId.toString());
            assertThat(MDC.get(TraceConstants.WORKSPACE_ID)).isEqualTo(workspaceId.toString());
            assertThat(MDC.get(TraceConstants.PROJECT_ID)).isNull();
            return "consumed";
        });

        assertThat(interceptor.invoke(invocation)).isEqualTo("consumed");
        assertThat(MDC.get(TraceConstants.TRACE_ID)).isEqualTo("worker-trace");
        assertThat(MDC.get(TraceConstants.PROJECT_ID)).isEqualTo("stale-project");
        assertThat(MDC.get(TraceConstants.EVENT_ID)).isNull();
    }

    @Test
    void clearsMdcAfterListenerFailureAndRejectsHeaderLogInjection() throws Throwable {
        var message = MessageBuilder.withBody(new byte[0])
                .setHeader(EventHeaders.TRACE_ID, "bad\ntrace")
                .setHeader(EventHeaders.EVENT_ID, "bad\nevent")
                .build();
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[] {message});
        when(invocation.proceed()).thenAnswer(ignored -> {
            assertThat(MDC.get(TraceConstants.TRACE_ID)).matches("[a-f0-9]{32}");
            assertThat(MDC.get(TraceConstants.EVENT_ID)).isNull();
            throw new IllegalStateException("listener failed");
        });

        assertThatThrownBy(() -> interceptor.invoke(invocation))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
