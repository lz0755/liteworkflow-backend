package com.liteworkflow.identity.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.liteworkflow.identity.domain.LocalOutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitIdentityEventPublisherTest {

    @Test
    void returnsOnlyAfterBrokerAck() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        RabbitIdentityEventPublisher publisher = new RabbitIdentityEventPublisher(template, Duration.ofSeconds(1));

        assertThatCode(() -> publisher.publish(event())).doesNotThrowAnyException();
    }

    @Test
    void rejectsBrokerNack() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        RabbitIdentityEventPublisher publisher = new RabbitIdentityEventPublisher(template, Duration.ofSeconds(1));

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("negatively acknowledged");
    }

    @Test
    void rejectsMandatoryReturnEvenWhenConfirmIsAck() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(
                    message, 312, "NO_ROUTE", "identity.event.exchange", "identity.user.registered"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        RabbitIdentityEventPublisher publisher = new RabbitIdentityEventPublisher(template, Duration.ofSeconds(1));

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unroutable");
    }

    @Test
    void rejectsMissingConfirmInsteadOfAssumingDelivery() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitIdentityEventPublisher publisher = new RabbitIdentityEventPublisher(template, Duration.ofMillis(5));

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirm was not received");
    }

    private LocalOutboxEvent event() {
        Instant now = Instant.parse("2026-07-10T00:00:00Z");
        return new LocalOutboxEvent(
                UUID.randomUUID(),
                "identity.user.registered",
                "identity.event.exchange",
                "identity.user.registered",
                "IDENTITY_USER",
                UUID.randomUUID(),
                "{\"payload\":{\"version\":1}}",
                now);
    }
}
