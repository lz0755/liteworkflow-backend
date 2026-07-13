package com.liteworkflow.common.mq.event;

import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

public final class JsonEventMessageFactory {

    private JsonEventMessageFactory() {}

    public static Message create(byte[] body, UUID eventId, String eventType, int eventVersion, String traceId) {
        var builder = MessageBuilder.withBody(body)
                .setContentType("application/json")
                .setHeader(EventHeaders.EVENT_ID, eventId.toString())
                .setHeader(EventHeaders.EVENT_TYPE, eventType)
                .setHeader(EventHeaders.EVENT_VERSION, eventVersion);
        if (traceId != null && !traceId.isBlank()) builder.setHeader(EventHeaders.TRACE_ID, traceId);
        return builder.build();
    }
}
