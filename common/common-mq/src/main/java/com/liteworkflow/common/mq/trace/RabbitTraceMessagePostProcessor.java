package com.liteworkflow.common.mq.trace;

import com.liteworkflow.common.core.trace.TraceIds;
import com.liteworkflow.common.mq.event.EventHeaders;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;

/** Ensures every published message carries a safe trace header. */
public final class RabbitTraceMessagePostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) {
        Object existing = message.getMessageProperties().getHeaders().get(EventHeaders.TRACE_ID);
        String existingValue = existing == null ? null : existing.toString();
        String traceId = TraceIds.safeOrNull(existingValue);
        if (traceId == null) {
            traceId = TraceIds.resolve(TraceIds.current());
            message.getMessageProperties().setHeader(EventHeaders.TRACE_ID, traceId);
        }
        return message;
    }
}
