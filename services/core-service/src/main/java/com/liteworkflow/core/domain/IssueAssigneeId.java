package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record IssueAssigneeId(
        @Column(name = "issue_id") UUID issueId,
        @Column(name = "user_id") UUID userId) implements Serializable {
}
