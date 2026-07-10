package com.liteworkflow.identity.outbox;

import com.liteworkflow.common.core.trace.TraceIds;
import com.liteworkflow.common.mq.event.EventHeaders;
import com.liteworkflow.identity.domain.LocalOutboxEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitIdentityEventPublisher implements IdentityEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitIdentityEventPublisher(RabbitTemplate rabbitTemplate, Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(LocalOutboxEvent event) {
        Message message = MessageBuilder.withBody(event.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setHeader(EventHeaders.EVENT_ID, event.getId().toString())
                .setHeader(EventHeaders.EVENT_TYPE, event.getEventType())
                .setHeader(EventHeaders.EVENT_VERSION, 1)
                .setHeader(EventHeaders.TRACE_ID, TraceIds.current())
                .build();
        CorrelationData correlationData = new CorrelationData(event.getId().toString());
        rabbitTemplate.send(event.getExchangeName(), event.getRoutingKey(), message, correlationData);
        awaitBrokerAcceptance(correlationData);
    }

    private void awaitBrokerAcceptance(CorrelationData correlationData) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeout.toNanos(), TimeUnit.NANOSECONDS);
            // Rabbit guarantees a mandatory return is populated before its publisher confirm completes.
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned the identity event as unroutable");
            }
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ negatively acknowledged the identity event");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting RabbitMQ publisher confirm", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ publisher confirm was not received", exception);
        }
    }
}
