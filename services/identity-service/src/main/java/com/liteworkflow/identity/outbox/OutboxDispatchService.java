package com.liteworkflow.identity.outbox;

import com.liteworkflow.identity.config.IdentityProperties;
import com.liteworkflow.identity.domain.LocalOutboxEvent;
import com.liteworkflow.identity.domain.OutboxStatus;
import com.liteworkflow.identity.repository.LocalOutboxEventRepository;
import java.time.Clock;
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
    private final IdentityEventPublisher publisher;
    private final IdentityProperties properties;
    private final Clock clock;

    public OutboxDispatchService(
            LocalOutboxEventRepository repository,
            IdentityEventPublisher publisher,
            IdentityProperties properties,
            Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    /** Delivers at least once. Consumers must de-duplicate by eventId. */
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
            // No payload, email, token, or exception message is logged.
            log.warn("Identity outbox delivery failed for eventId={}, eventType={}", eventId, event.getEventType());
        }
    }

    @Transactional
    public void recoverPending() {
        repository.findRecoverableIds(clock.instant(), PageRequest.of(0, 100)).forEach(this::dispatch);
    }

    private java.time.Duration retryDelay(int retryCount) {
        long multiplier = 1L << Math.min(retryCount, 6);
        java.time.Duration base = properties.getOutbox().getRetryDelay();
        try {
            return base.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return java.time.Duration.ofHours(1);
        }
    }
}
