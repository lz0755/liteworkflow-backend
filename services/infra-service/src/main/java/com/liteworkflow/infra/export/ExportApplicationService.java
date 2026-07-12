package com.liteworkflow.infra.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.common.core.trace.TraceIds;
import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.infra.config.FileStorageProperties;
import com.liteworkflow.infra.file.AccessContext;
import com.liteworkflow.infra.file.FileAccessAuthorizer;
import com.liteworkflow.infra.file.FileScope;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportApplicationService {

    private final ExportJobRepository jobs;
    private final ExportFileRepository files;
    private final ExportJobStore store;
    private final FileAccessAuthorizer access;
    private final ObjectStorage storage;
    private final FileStorageProperties storageProperties;
    private final ObjectMapper json;
    private final Clock clock;

    public ExportApplicationService(
            ExportJobRepository jobs,
            ExportFileRepository files,
            ExportJobStore store,
            FileAccessAuthorizer access,
            ObjectStorage storage,
            FileStorageProperties storageProperties,
            ObjectMapper json,
            Clock clock) {
        this.jobs = jobs;
        this.files = files;
        this.store = store;
        this.access = access;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.json = json;
        this.clock = clock;
    }

    public ExportJobResponse create(UUID userId, CreateIssueExportRequest request) {
        AccessContext context = access.authorize(
                userId, FileScope.PROJECT, request.projectId(), FileAccessAuthorizer.AccessAction.READ);
        if (context.workspaceId() == null || !request.projectId().equals(context.projectId())) {
            throw new BizException(ExportErrorCode.EXPORT_REQUEST_FAILED);
        }

        UUID jobId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        ExportJob job = new ExportJob(
                jobId, request.format(), context.workspaceId(), request.projectId(), userId, now);
        var payload = new IssueExportRequestedPayload(jobId, request.projectId(), userId, request.format());
        var envelope = new EventEnvelope<>(
                eventId,
                ExportAmqpConfiguration.REQUESTED,
                1,
                now,
                new EventScope(context.workspaceId(), request.projectId(), userId),
                jobId,
                payload,
                Map.of());
        ExportOutboxEvent outbox = new ExportOutboxEvent(
                eventId,
                jobId,
                ExportAmqpConfiguration.REQUESTED,
                ExportAmqpConfiguration.EXCHANGE,
                ExportAmqpConfiguration.REQUESTED,
                serialize(envelope),
                TraceIds.resolve(TraceIds.current()),
                now);
        store.create(job, outbox);
        return ExportJobResponse.from(job, null);
    }

    @Transactional(readOnly = true)
    public ExportJobResponse get(UUID userId, UUID jobId) {
        ExportJob job = requireOwned(userId, jobId);
        ExportFile file = files.findByExportJobId(jobId).orElse(null);
        return ExportJobResponse.from(job, file);
    }

    public ExportDownload download(UUID userId, UUID jobId) {
        ExportJob job = requireOwned(userId, jobId);
        if (job.getStatus() != ExportJobStatus.COMPLETED) {
            throw new BizException(ExportErrorCode.EXPORT_NOT_READY);
        }
        access.authorize(userId, FileScope.PROJECT, job.getProjectId(), FileAccessAuthorizer.AccessAction.READ);
        ExportFile file = files.findByExportJobId(jobId)
                .orElseThrow(() -> new BizException(ExportErrorCode.EXPORT_NOT_READY));
        if (!storageProperties.getS3().getBucket().equals(file.getBucket())) {
            throw new BizException(ExportErrorCode.EXPORT_NOT_FOUND);
        }
        return new ExportDownload(file, storage.get(file.getObjectKey()));
    }

    private ExportJob requireOwned(UUID userId, UUID jobId) {
        return jobs.findByIdAndRequestedBy(jobId, userId)
                .orElseThrow(() -> new BizException(ExportErrorCode.EXPORT_NOT_FOUND));
    }

    private String serialize(EventEnvelope<IssueExportRequestedPayload> envelope) {
        try {
            return json.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new BizException(ExportErrorCode.EXPORT_REQUEST_FAILED, null, exception);
        }
    }
}
