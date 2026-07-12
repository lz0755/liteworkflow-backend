package com.liteworkflow.infra.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.liteworkflow.common.core.event.EventEnvelope;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ExportStatusEventConsumer {

    private final ExportStatusProjectionService projection;

    public ExportStatusEventConsumer(ExportStatusProjectionService projection) {
        this.projection = projection;
    }

    @RabbitListener(queues = ExportAmqpConfiguration.STATUS_QUEUE)
    public void consume(EventEnvelope<JsonNode> envelope) {
        projection.consume(envelope);
    }
}
