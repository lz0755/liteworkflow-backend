package com.liteworkflow.infra.export;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "infra", name = "export_jobs")
public class ExportJob {

    @Id
    private UUID id;

    @Column(name = "export_type", nullable = false, length = 32)
    private String exportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_format", nullable = false, length = 16)
    private ExportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExportJobStatus status;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected ExportJob() {
    }

    public ExportJob(
            UUID id,
            ExportFormat format,
            UUID workspaceId,
            UUID projectId,
            UUID requestedBy,
            Instant createdAt) {
        this.id = id;
        this.exportType = "ISSUES";
        this.format = format;
        this.status = ExportJobStatus.PENDING;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
    }

    public void complete(Instant now) {
        if (status == ExportJobStatus.PENDING) {
            status = ExportJobStatus.COMPLETED;
            completedAt = now;
            failureCode = null;
        }
    }

    public void fail(String code, Instant now) {
        if (status == ExportJobStatus.PENDING) {
            status = ExportJobStatus.FAILED;
            failureCode = safeFailureCode(code);
            completedAt = now;
        }
    }

    private String safeFailureCode(String code) {
        String value = code == null || code.isBlank() ? "EXPORT_FAILED" : code.trim();
        return value.substring(0, Math.min(value.length(), 64));
    }

    public UUID getId() { return id; }
    public ExportFormat getFormat() { return format; }
    public ExportJobStatus getStatus() { return status; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getProjectId() { return projectId; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getFailureCode() { return failureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
