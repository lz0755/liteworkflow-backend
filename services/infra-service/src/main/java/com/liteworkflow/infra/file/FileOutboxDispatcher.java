package com.liteworkflow.infra.file;

import com.liteworkflow.common.core.trace.TraceIds;
import com.liteworkflow.common.mq.event.EventHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
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
@ConditionalOnProperty(prefix = "liteworkflow.storage", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class FileOutboxDispatcher {
    private final FileOutboxRepository repository;
    private final RabbitTemplate rabbit;
    private final Clock clock;
    public FileOutboxDispatcher(FileOutboxRepository repository, RabbitTemplate rabbit, Clock clock) {
        this.repository = repository; this.rabbit = rabbit; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${liteworkflow.storage.outbox-delay:5000}")
    @Transactional
    public void dispatch() {
        var events = repository.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                List.of(FileOutboxEvent.Status.PENDING, FileOutboxEvent.Status.FAILED), clock.instant(), PageRequest.of(0, 50));
        for (FileOutboxEvent event : events) {
            try {
                var message = MessageBuilder.withBody(event.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                        .setContentType("application/json")
                        .setHeader(EventHeaders.EVENT_ID, event.getId().toString())
                        .setHeader(EventHeaders.EVENT_TYPE, event.getEventType())
                        .setHeader(EventHeaders.EVENT_VERSION, 1)
                        .setHeader(EventHeaders.TRACE_ID, TraceIds.resolve(TraceIds.current())).build();
                CorrelationData correlation = new CorrelationData(event.getId().toString());
                rabbit.send("work.event.exchange", event.getRoutingKey(), message, correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) {
                    throw new IllegalStateException("RabbitMQ did not accept the file outbox event");
                }
                event.published(clock.instant());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                event.failed(clock.instant().plus(Duration.ofSeconds(10)));
            }
        }
    }
}
