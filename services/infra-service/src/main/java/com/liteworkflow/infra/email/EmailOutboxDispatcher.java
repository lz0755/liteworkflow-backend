package com.liteworkflow.infra.email;

import com.liteworkflow.infra.notification.NotificationProperties;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "liteworkflow.notification.email",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EmailOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);

    private final EmailOutboxRepository repository;
    private final EmailDeliveryService deliveryService;
    private final NotificationProperties properties;
    private final Clock clock;

    public EmailOutboxDispatcher(
            EmailOutboxRepository repository,
            EmailDeliveryService deliveryService,
            NotificationProperties properties,
            Clock clock) {
        this.repository = repository;
        this.deliveryService = deliveryService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${liteworkflow.notification.email.dispatch-delay:5s}")
    public void dispatch() {
        List<java.util.UUID> due = repository.findDueIds(
                List.of(EmailDeliveryStatus.PENDING, EmailDeliveryStatus.RETRYING),
                clock.instant(),
                PageRequest.of(0, properties.getEmail().getBatchSize()));
        for (java.util.UUID jobId : due) {
            try {
                deliveryService.deliver(jobId);
            } catch (RuntimeException exception) {
                // The row remains due. Avoid exception text because provider errors can echo sensitive headers.
                log.error("Email outbox dispatch failed jobId={}", jobId);
            }
        }
    }
}
