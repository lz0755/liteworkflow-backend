package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "issue_comments")
public class IssueComment {

    @Id
    private UUID id;

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected IssueComment() {
    }

    public IssueComment(UUID id, UUID issueId, UUID authorId, String body, Instant now) {
        this.id = id;
        this.issueId = issueId;
        this.authorId = authorId;
        this.body = body;
        this.updatedBy = authorId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String body, UUID actorId, Instant now) {
        this.body = body;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void delete(UUID actorId, Instant now) {
        this.body = null;
        this.updatedBy = actorId;
        this.deletedBy = actorId;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getIssueId() { return issueId; }
    public UUID getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public UUID getUpdatedBy() { return updatedBy; }
    public UUID getDeletedBy() { return deletedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
