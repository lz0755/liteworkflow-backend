package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "issues")
public class Issue {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "issue_number", nullable = false, updatable = false)
    private long issueNumber;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "state_id", nullable = false)
    private UUID stateId;

    @Column(name = "client_request_id", updatable = false)
    private UUID clientRequestId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected Issue() {
    }

    public Issue(
            UUID id,
            UUID projectId,
            long issueNumber,
            String title,
            String description,
            UUID stateId,
            UUID clientRequestId,
            UUID actorId,
            Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.issueNumber = issueNumber;
        this.title = title;
        this.description = description;
        this.stateId = stateId;
        this.clientRequestId = clientRequestId;
        this.createdBy = actorId;
        this.updatedBy = actorId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, String description, UUID actorId, Instant now) {
        this.title = title;
        this.description = description;
        touch(actorId, now);
    }

    public void moveTo(UUID stateId, UUID actorId, Instant now) {
        this.stateId = stateId;
        touch(actorId, now);
    }

    public void touch(UUID actorId, Instant now) {
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void delete(UUID actorId, Instant now) {
        this.deletedAt = now;
        touch(actorId, now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public long getIssueNumber() {
        return issueNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getStateId() {
        return stateId;
    }

    public UUID getClientRequestId() {
        return clientRequestId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getRowVersion() {
        return rowVersion;
    }
}
