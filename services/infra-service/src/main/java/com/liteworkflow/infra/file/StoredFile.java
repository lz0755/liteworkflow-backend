package com.liteworkflow.infra.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "infra", name = "stored_files")
public class StoredFile {
    @Id private UUID id;
    @Column(name = "document_id", nullable = false, updatable = false) private UUID documentId;
    @Column(name = "source_version", nullable = false, updatable = false) private long sourceVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private FilePurpose purpose;
    @Enumerated(EnumType.STRING) @Column(name = "scope_type", nullable = false, length = 16) private FileScope scopeType;
    @Column(name = "scope_id", nullable = false) private UUID scopeId;
    @Column(name = "workspace_id") private UUID workspaceId;
    @Column(name = "project_id") private UUID projectId;
    @Column(name = "issue_id") private UUID issueId;
    @Column(nullable = false, length = 100) private String bucket;
    @Column(name = "object_key", nullable = false, unique = true, length = 1024) private String objectKey;
    @Column(name = "original_name", nullable = false, length = 255) private String originalName;
    @Column(nullable = false, length = 16) private String extension;
    @Column(name = "content_type", nullable = false, length = 128) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "sha256_hex", nullable = false, length = 64) private String sha256Hex;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private FileStatus status;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Column(name = "delete_attempts", nullable = false) private int deleteAttempts;

    protected StoredFile() {}

    public StoredFile(UUID id, FilePurpose purpose, UUID scopeId, AccessContext context, String bucket,
            String objectKey, ValidatedFile file, UUID createdBy, Instant now) {
        this(id, id, 1, purpose, scopeId, context, bucket, objectKey, file, createdBy, now);
    }

    public StoredFile(UUID id, UUID documentId, long sourceVersion, FilePurpose purpose, UUID scopeId,
            AccessContext context, String bucket, String objectKey, ValidatedFile file, UUID createdBy, Instant now) {
        if (sourceVersion < 1) throw new IllegalArgumentException("sourceVersion must be positive");
        this.id = id; this.documentId = documentId; this.sourceVersion = sourceVersion;
        this.purpose = purpose; this.scopeType = purpose.scope(); this.scopeId = scopeId;
        this.workspaceId = context.workspaceId(); this.projectId = context.projectId(); this.issueId = context.issueId();
        this.bucket = bucket; this.objectKey = objectKey; this.originalName = file.originalName();
        this.extension = file.extension(); this.contentType = file.contentType(); this.sizeBytes = file.bytes().length;
        this.sha256Hex = file.sha256Hex(); this.status = FileStatus.ACTIVE; this.createdBy = createdBy;
        this.createdAt = now;
    }

    public void markPendingDelete(Instant now) { this.status = FileStatus.PENDING_DELETE; this.deletedAt = now; }
    public void markDeleted() { this.status = FileStatus.DELETED; }
    public void markDeleteFailed() { this.status = FileStatus.DELETE_FAILED; this.deleteAttempts++; }
    public void retryDelete() { this.status = FileStatus.PENDING_DELETE; }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public long getSourceVersion() { return sourceVersion; }
    public FilePurpose getPurpose() { return purpose; }
    public FileScope getScopeType() { return scopeType; }
    public UUID getScopeId() { return scopeId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getProjectId() { return projectId; }
    public UUID getIssueId() { return issueId; }
    public String getBucket() { return bucket; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalName() { return originalName; }
    public String getExtension() { return extension; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256Hex() { return sha256Hex; }
    public FileStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
