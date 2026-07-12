package com.liteworkflow.infra.export;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "infra", name = "export_files")
public class ExportFile {

    @Id
    private UUID id;

    @Column(name = "export_job_id", nullable = false, unique = true, updatable = false)
    private UUID exportJobId;

    @Column(nullable = false, length = 100, updatable = false)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 1024, unique = true, updatable = false)
    private String objectKey;

    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "sha256_hex", nullable = false, length = 64, updatable = false)
    private String sha256Hex;

    @Column(name = "row_count", nullable = false, updatable = false)
    private long rowCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExportFile() {
    }

    public ExportFile(
            UUID id,
            UUID exportJobId,
            String bucket,
            String objectKey,
            String fileName,
            String contentType,
            long sizeBytes,
            String sha256Hex,
            long rowCount,
            Instant createdAt) {
        this.id = id;
        this.exportJobId = exportJobId;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256Hex = sha256Hex;
        this.rowCount = rowCount;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getExportJobId() { return exportJobId; }
    public String getBucket() { return bucket; }
    public String getObjectKey() { return objectKey; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256Hex() { return sha256Hex; }
    public long getRowCount() { return rowCount; }
    public Instant getCreatedAt() { return createdAt; }
}
