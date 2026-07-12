package com.liteworkflow.core.export;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ExportRequestRecovererTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ExportOutcomeRecorder outcomes = mock(ExportOutcomeRecorder.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final ExportRequestRecoverer recoverer = new ExportRequestRecoverer(json, outcomes, rabbit);

    @Test
    void marksKnownJobFailedAndCopiesExhaustedMessageToDlq() throws Exception {
        EventEnvelope<IssueExportRequestedPayload> envelope = envelope();
        Message message = MessageBuilder.withBody(json.writeValueAsBytes(envelope))
                .setContentType("application/json")
                .build();

        recoverer.recover(message, new IllegalStateException("storage unavailable"));

        verify(outcomes).recordFailed(envelope, "EXPORT_PROCESSING_FAILED");
        verify(rabbit).send(
                CoreExportAmqpConfiguration.REQUEST_DLX,
                CoreExportAmqpConfiguration.REQUEST_DLQ,
                message);
    }

    @Test
    void malformedPoisonMessageStillMovesToDlqWithoutInventingAJob() {
        Message message = MessageBuilder.withBody("not-json".getBytes()).build();

        recoverer.recover(message, new IllegalArgumentException("bad message"));

        verify(outcomes, never()).recordFailed(any(), any());
        verify(rabbit).send(
                CoreExportAmqpConfiguration.REQUEST_DLX,
                CoreExportAmqpConfiguration.REQUEST_DLQ,
                message);
    }

    private EventEnvelope<IssueExportRequestedPayload> envelope() {
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        return new EventEnvelope<>(
                UUID.randomUUID(),
                CoreExportAmqpConfiguration.REQUESTED,
                1,
                Instant.parse("2026-07-12T00:00:00Z"),
                new EventScope(UUID.randomUUID(), projectId, userId),
                jobId,
                new IssueExportRequestedPayload(jobId, projectId, userId, IssueExportFormat.CSV),
                Map.of());
    }
}
