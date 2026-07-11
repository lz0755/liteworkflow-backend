package com.liteworkflow.core.outbox;

import com.liteworkflow.core.config.CoreProperties;
import com.liteworkflow.core.domain.LocalOutboxEvent;
import com.liteworkflow.core.domain.OutboxStatus;
import com.liteworkflow.core.repository.LocalOutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDispatchService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchService.class);

    private final LocalOutboxEventRepository repository;
    private final CoreEventPublisher publisher;
    private final CoreProperties properties;
    private final Clock clock;

    public OutboxDispatchService(
            LocalOutboxEventRepository repository,
            CoreEventPublisher publisher,
            CoreProperties properties,
            Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(UUID eventId) {
        LocalOutboxEvent event = repository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.getStatus() == OutboxStatus.PUBLISHED || event.getStatus() == OutboxStatus.DEAD) {
            return;
        }
        Instant now = clock.instant();
        try {
            publisher.publish(event);
            event.markPublished(now);
        } catch (Exception exception) {
            event.markFailed(
                    now,
                    now.plus(retryDelay(event.getRetryCount())),
                    exception.getClass().getSimpleName(),
                    properties.getOutbox().getMaxRetries());
            log.warn("Core outbox delivery failed for eventId={}, eventType={}", eventId, event.getEventType());
        }
    }

    @Transactional
    public void recoverPending() {
        repository.findRecoverableIds(clock.instant(), PageRequest.of(0, 100)).forEach(this::dispatch);
    }

    private Duration retryDelay(int retryCount) {
        long multiplier = 1L << Math.min(retryCount, 6);
        try {
            return properties.getOutbox().getRetryDelay().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return Duration.ofHours(1);
        }
    }
}
