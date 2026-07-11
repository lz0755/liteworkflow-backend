package com.liteworkflow.infra.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.liteworkflow.common.core.event.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationProjectionService projectionService;

    public NotificationEventConsumer(NotificationProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @RabbitListener(queues = NotificationAmqpConfiguration.QUEUE)
    public void consume(EventEnvelope<JsonNode> envelope) {
        boolean applied = projectionService.consume(envelope);
        log.debug(
                "Collaboration notification event consumed eventId={}, eventType={}, applied={}",
                envelope.eventId(),
                envelope.eventType(),
                applied);
    }
}
