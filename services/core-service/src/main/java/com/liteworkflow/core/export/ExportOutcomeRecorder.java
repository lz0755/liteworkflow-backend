package com.liteworkflow.core.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.core.domain.ConsumedEvent;
import com.liteworkflow.core.domain.LocalOutboxEvent;
import com.liteworkflow.core.outbox.OutboxQueued;
import com.liteworkflow.core.repository.ConsumedEventRepository;
import com.liteworkflow.core.repository.LocalOutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportOutcomeRecorder {

    private static final String EXPORT_AGGREGATE = "EXPORT_JOB";

    private final ConsumedEventRepository consumedEvents;
    private final LocalOutboxEventRepository outbox;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ExportOutcomeRecorder(
            ConsumedEventRepository consumedEvents,
            LocalOutboxEventRepository outbox,
            ObjectMapper json,
            ApplicationEventPublisher events,
            Clock clock) {
        this.consumedEvents = consumedEvents;
        this.outbox = outbox;
        this.json = json;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public boolean recordCompleted(
            EventEnvelope<IssueExportRequestedPayload> request,
            IssueExportCompletedPayload payload) {
        return record(request, CoreExportAmqpConfiguration.COMPLETED, payload);
    }

    @Transactional
    public boolean recordFailed(
            EventEnvelope<IssueExportRequestedPayload> request,
            String failureCode) {
        var payload = new IssueExportFailedPayload(
                request.payload().jobId(), request.payload().projectId(), failureCode);
        return record(request, CoreExportAmqpConfiguration.FAILED, payload);
    }

    private boolean record(
            EventEnvelope<IssueExportRequestedPayload> request,
            String eventType,
            Object payload) {
        if (consumedEvents.existsById(request.eventId())) return false;

        Instant now = clock.instant();
        UUID outcomeEventId = UUID.randomUUID();
        var envelope = new EventEnvelope<>(
                outcomeEventId,
                eventType,
                1,
                now,
                request.scope(),
                request.payload().jobId(),
                payload,
                Map.of());
        outbox.save(new LocalOutboxEvent(
                outcomeEventId,
                eventType,
                CoreExportAmqpConfiguration.EXCHANGE,
                eventType,
                EXPORT_AGGREGATE,
                request.payload().jobId(),
                request.scope().workspaceId(),
                request.payload().projectId(),
                request.payload().requestedBy(),
                json.valueToTree(envelope),
                now));
        consumedEvents.save(new ConsumedEvent(request.eventId(), request.eventType(), now));
        events.publishEvent(new OutboxQueued(outcomeEventId));
        return true;
    }
}
