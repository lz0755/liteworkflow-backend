package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@IdClass(IssueSubscriberId.class)
@Table(schema = "core", name = "issue_subscribers")
public class IssueSubscriber {

    @Id
    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "subscribed_by", nullable = false, updatable = false)
    private UUID subscribedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IssueSubscriber() {
    }

    public IssueSubscriber(UUID issueId, UUID userId, UUID subscribedBy, Instant createdAt) {
        this.issueId = issueId;
        this.userId = userId;
        this.subscribedBy = subscribedBy;
        this.createdAt = createdAt;
    }

    public UUID getIssueId() { return issueId; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}
