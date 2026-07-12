package com.liteworkflow.infra.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.file.storage.ObjectKeys;
import com.liteworkflow.infra.config.FileStorageProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportStatusProjectionService {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern FAILURE_CODE = Pattern.compile("[A-Z0-9_]{1,64}");

    private final ExportJobRepository jobs;
    private final ExportFileRepository files;
    private final ExportConsumedEventRepository consumedEvents;
    private final FileStorageProperties storageProperties;
    private final Clock clock;

    public ExportStatusProjectionService(
            ExportJobRepository jobs,
            ExportFileRepository files,
            ExportConsumedEventRepository consumedEvents,
            FileStorageProperties storageProperties,
            Clock clock) {
        this.jobs = jobs;
        this.files = files;
        this.consumedEvents = consumedEvents;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }

    @Transactional
    public boolean consume(EventEnvelope<JsonNode> envelope) {
        if (consumedEvents.existsById(envelope.eventId())) {
            return false;
        }
        if (!ExportAmqpConfiguration.COMPLETED.equals(envelope.eventType())
                && !ExportAmqpConfiguration.FAILED.equals(envelope.eventType())) {
            throw new IllegalArgumentException("Unsupported export outcome event type");
        }

        JsonNode payload = envelope.payload();
        UUID jobId = uuid(payload, "jobId");
        UUID projectId = uuid(payload, "projectId");
        if (!jobId.equals(envelope.aggregateId())
                || envelope.scope().projectId() == null
                || !projectId.equals(envelope.scope().projectId())) {
            throw new IllegalArgumentException("Export outcome scope does not match payload");
        }

        ExportJob job = jobs.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Export job does not exist"));
        if (!job.getProjectId().equals(projectId)
                || !job.getWorkspaceId().equals(envelope.scope().workspaceId())) {
            throw new IllegalArgumentException("Export outcome does not match job scope");
        }

        Instant now = clock.instant();
        if (job.getStatus() != ExportJobStatus.PENDING) {
            consumedEvents.save(new ExportConsumedEvent(envelope.eventId(), envelope.eventType(), now));
            return false;
        }

        if (ExportAmqpConfiguration.COMPLETED.equals(envelope.eventType())) {
            complete(job, payload, now);
        } else {
            fail(job, payload, now);
        }
        consumedEvents.save(new ExportConsumedEvent(envelope.eventId(), envelope.eventType(), now));
        return true;
    }

    private void complete(ExportJob job, JsonNode payload, Instant now) {
        ExportFormat format;
        try {
            format = ExportFormat.valueOf(requiredText(payload, "format").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Export outcome format is invalid", exception);
        }
        String bucket = requiredText(payload, "bucket");
        String objectKey = ObjectKeys.requireSafe(requiredText(payload, "objectKey"));
        String fileName = requiredText(payload, "fileName");
        String contentType = requiredText(payload, "contentType");
        String sha256Hex = requiredText(payload, "sha256Hex").toLowerCase(Locale.ROOT);
        long sizeBytes = requiredLong(payload, "sizeBytes");
        long rowCount = requiredLong(payload, "rowCount");
        String expectedKey = ExportObjectNames.objectKey(
                job.getWorkspaceId(), job.getProjectId(), job.getId(), job.getFormat());
        String expectedName = ExportObjectNames.fileName(job.getProjectId(), job.getId(), job.getFormat());
        if (format != job.getFormat()
                || !expectedKey.equals(objectKey)
                || !expectedName.equals(fileName)
                || !storageProperties.getS3().getBucket().equals(bucket)
                || !format.contentType().equals(contentType)
                || sizeBytes < 0
                || rowCount < 0
                || !SHA256.matcher(sha256Hex).matches()) {
            throw new IllegalArgumentException("Export file metadata is invalid");
        }
        files.save(new ExportFile(
                UUID.randomUUID(),
                job.getId(),
                bucket,
                objectKey,
                fileName,
                contentType,
                sizeBytes,
                sha256Hex,
                rowCount,
                now));
        job.complete(now);
    }

    private void fail(ExportJob job, JsonNode payload, Instant now) {
        String code = requiredText(payload, "failureCode").toUpperCase(Locale.ROOT);
        if (!FAILURE_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Export failure code is invalid");
        }
        job.fail(code, now);
    }

    private UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(requiredText(node, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Export outcome " + field + " is invalid", exception);
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Export outcome is missing " + field);
        }
        return value.textValue();
    }

    private long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new IllegalArgumentException("Export outcome is missing " + field);
        }
        return value.longValue();
    }
}
