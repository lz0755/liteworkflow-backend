package com.liteworkflow.core.directory;

import com.liteworkflow.common.core.event.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserDirectoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserDirectoryEventConsumer.class);

    private final UserDirectoryProjectionService projectionService;

    public UserDirectoryEventConsumer(UserDirectoryProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @RabbitListener(queues = CoreAmqpConfiguration.IDENTITY_USER_QUEUE)
    public void consume(EventEnvelope<IdentityUserEventPayload> envelope) {
        boolean applied = projectionService.consume(envelope);
        log.debug(
                "Identity directory event consumed eventId={}, eventType={}, sourceVersion={}, applied={}",
                envelope.eventId(),
                envelope.eventType(),
                envelope.payload().version(),
                applied);
    }
}
