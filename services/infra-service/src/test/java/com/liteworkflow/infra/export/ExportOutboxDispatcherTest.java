package com.liteworkflow.infra.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liteworkflow.common.mq.event.EventHeaders;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

class ExportOutboxDispatcherTest {

    @Test
    void schedulerPublishesThePersistedTraceEvenWhenMdcIsEmpty() {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        ExportOutboxRepository repository = mock(ExportOutboxRepository.class);
        ExportJobRepository jobs = mock(ExportJobRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        InfraExportProperties properties = new InfraExportProperties();
        ExportOutboxEvent event = new ExportOutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ExportAmqpConfiguration.REQUESTED,
                ExportAmqpConfiguration.EXCHANGE,
                ExportAmqpConfiguration.REQUESTED,
                "{}",
                "persisted-http-trace",
                now);
        when(repository.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                anyCollection(), eq(now), any(Pageable.class))).thenReturn(List.of(event));
        AtomicReference<Message> published = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            published.set(invocation.getArgument(2));
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(
                eq(ExportAmqpConfiguration.EXCHANGE),
                eq(ExportAmqpConfiguration.REQUESTED),
                any(Message.class),
                any(CorrelationData.class));
        MDC.clear();
        ExportOutboxDispatcher dispatcher = new ExportOutboxDispatcher(
                repository, jobs, rabbit, properties, Clock.fixed(now, ZoneOffset.UTC));

        dispatcher.dispatch();

        assertThat(published.get().getMessageProperties().getHeader(EventHeaders.TRACE_ID).toString())
                .isEqualTo("persisted-http-trace");
        assertThat(event.getStatus()).isEqualTo(ExportOutboxStatus.PUBLISHED);
        verify(repository).findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                anyCollection(), eq(now), any(Pageable.class));
    }
}
