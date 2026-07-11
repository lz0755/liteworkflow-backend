package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "issue_assignees")
public class IssueAssignee {

    @EmbeddedId
    private IssueAssigneeId id;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected IssueAssignee() {
    }

    public IssueAssignee(UUID issueId, UUID userId, UUID assignedBy, Instant assignedAt) {
        this.id = new IssueAssigneeId(issueId, userId);
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public UUID getIssueId() {
        return id.issueId();
    }

    public UUID getUserId() {
        return id.userId();
    }
}
