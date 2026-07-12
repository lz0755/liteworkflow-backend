package com.liteworkflow.common.mq.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.mq.event.EventHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageBuilder;

class RabbitTraceMessagePostProcessorTest {

    private final RabbitTraceMessagePostProcessor processor = new RabbitTraceMessagePostProcessor();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void addsCurrentTraceWhenProducerDidNotSetOne() {
        MDC.put(TraceConstants.TRACE_ID, "publisher-trace");
        var message = processor.postProcessMessage(MessageBuilder.withBody(new byte[0]).build());
        Object traceHeader = message.getMessageProperties().getHeader(EventHeaders.TRACE_ID);

        assertThat(traceHeader).isEqualTo("publisher-trace");
    }

    @Test
    void preservesASafeExplicitTraceAndReplacesAnUnsafeOne() {
        var explicit = MessageBuilder.withBody(new byte[0])
                .setHeader(EventHeaders.TRACE_ID, "original-trace")
                .build();
        Object explicitTrace = processor.postProcessMessage(explicit).getMessageProperties()
                .getHeader(EventHeaders.TRACE_ID);
        assertThat(explicitTrace).isEqualTo("original-trace");

        var unsafe = MessageBuilder.withBody(new byte[0])
                .setHeader(EventHeaders.TRACE_ID, "forged\ntrace")
                .build();
        assertThat(processor.postProcessMessage(unsafe).getMessageProperties()
                        .getHeader(EventHeaders.TRACE_ID).toString())
                .matches("[a-f0-9]{32}");
    }
}
