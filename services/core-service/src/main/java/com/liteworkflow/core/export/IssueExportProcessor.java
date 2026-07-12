package com.liteworkflow.core.export;

import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.file.storage.PutObjectRequest;
import com.liteworkflow.core.application.PermissionService;
import com.liteworkflow.core.repository.ConsumedEventRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IssueExportProcessor {

    private final ConsumedEventRepository consumedEvents;
    private final PermissionService permissions;
    private final IssueExportGenerator generator;
    private final ObjectStorage storage;
    private final CoreExportProperties properties;
    private final ExportOutcomeRecorder outcomes;

    public IssueExportProcessor(
            ConsumedEventRepository consumedEvents,
            PermissionService permissions,
            IssueExportGenerator generator,
            ObjectStorage storage,
            CoreExportProperties properties,
            ExportOutcomeRecorder outcomes) {
        this.consumedEvents = consumedEvents;
        this.permissions = permissions;
        this.generator = generator;
        this.storage = storage;
        this.properties = properties;
        this.outcomes = outcomes;
    }

    public boolean process(EventEnvelope<IssueExportRequestedPayload> envelope) {
        validate(envelope);
        if (consumedEvents.existsById(envelope.eventId())) return false;

        IssueExportRequestedPayload request = envelope.payload();
        permissions.requireProjectMember(request.projectId(), request.requestedBy());
        GeneratedIssueExport generated = generator.generate(
                request.jobId(), request.projectId(), request.format());
        try {
            String objectKey = ExportObjectNames.objectKey(
                    envelope.scope().workspaceId(), request.projectId(), request.jobId(), request.format());
            upload(request, generated, objectKey);
            var completed = new IssueExportCompletedPayload(
                    request.jobId(),
                    request.projectId(),
                    request.format(),
                    properties.getS3().getBucket(),
                    objectKey,
                    ExportObjectNames.fileName(request.projectId(), request.jobId(), request.format()),
                    request.format().contentType(),
                    generated.sizeBytes(),
                    generated.sha256Hex(),
                    generated.rowCount());
            return outcomes.recordCompleted(envelope, completed);
        } finally {
            IssueExportGenerator.deleteQuietly(generated.path());
        }
    }

    private void upload(
            IssueExportRequestedPayload request,
            GeneratedIssueExport generated,
            String objectKey) {
        try (InputStream input = Files.newInputStream(generated.path())) {
            storage.put(new PutObjectRequest(
                    objectKey,
                    input,
                    generated.sizeBytes(),
                    generated.format().contentType(),
                    Map.of("export-job-id", request.jobId().toString(), "sha256", generated.sha256Hex())));
        } catch (IOException exception) {
            throw new IssueExportGenerationException("Export file could not be uploaded", exception);
        }
    }

    private void validate(EventEnvelope<IssueExportRequestedPayload> envelope) {
        if (!CoreExportAmqpConfiguration.REQUESTED.equals(envelope.eventType()) || envelope.version() != 1) {
            throw new IllegalArgumentException("Unsupported issue export request event");
        }
        IssueExportRequestedPayload payload = envelope.payload();
        if (payload.jobId() == null
                || payload.projectId() == null
                || payload.requestedBy() == null
                || payload.format() == null
                || !payload.jobId().equals(envelope.aggregateId())
                || envelope.scope().workspaceId() == null
                || !payload.projectId().equals(envelope.scope().projectId())
                || !payload.requestedBy().equals(envelope.scope().actorId())) {
            throw new IllegalArgumentException("Issue export request scope is invalid");
        }
    }
}
