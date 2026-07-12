package com.liteworkflow.infra.export;

import com.liteworkflow.common.mq.event.EventHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        prefix = "liteworkflow.export",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ExportOutboxDispatcher {

    private final ExportOutboxRepository outbox;
    private final ExportJobRepository jobs;
    private final RabbitTemplate rabbit;
    private final InfraExportProperties properties;
    private final Clock clock;

    public ExportOutboxDispatcher(
            ExportOutboxRepository outbox,
            ExportJobRepository jobs,
            RabbitTemplate rabbit,
            InfraExportProperties properties,
            Clock clock) {
        this.outbox = outbox;
        this.jobs = jobs;
        this.rabbit = rabbit;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${liteworkflow.export.outbox-delay:1000}")
    @Transactional
    public void dispatch() {
        List<ExportOutboxEvent> events = outbox
                .findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                        List.of(ExportOutboxStatus.PENDING, ExportOutboxStatus.FAILED),
                        clock.instant(),
                        PageRequest.of(0, 50));
        events.forEach(this::publish);
    }

    private void publish(ExportOutboxEvent event) {
        Instant now = clock.instant();
        try {
            var message = MessageBuilder.withBody(event.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                    .setContentType("application/json")
                    .setHeader(EventHeaders.EVENT_ID, event.getId().toString())
                    .setHeader(EventHeaders.EVENT_TYPE, event.getEventType())
                    .setHeader(EventHeaders.EVENT_VERSION, 1)
                    .setHeader(EventHeaders.TRACE_ID, event.getTraceId())
                    .build();
            CorrelationData correlation = new CorrelationData(event.getId().toString());
            rabbit.send(event.getExchangeName(), event.getRoutingKey(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    properties.getPublisherConfirmTimeout().toNanos(), TimeUnit.NANOSECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ did not accept export request");
            }
            event.markPublished(now);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            boolean dead = event.markFailed(
                    now,
                    now.plus(properties.getRetryDelay()),
                    properties.getMaxPublishAttempts());
            if (dead) {
                jobs.findByIdForUpdate(event.getExportJobId())
                        .ifPresent(job -> job.fail("REQUEST_DELIVERY_FAILED", now));
            }
        }
    }
}
