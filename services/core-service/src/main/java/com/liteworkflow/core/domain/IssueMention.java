package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@IdClass(IssueMentionId.class)
@Table(schema = "core", name = "issue_mentions")
public class IssueMention {

    @Id
    @Column(name = "comment_id", nullable = false, updatable = false)
    private UUID commentId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "mentioned_by", nullable = false, updatable = false)
    private UUID mentionedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IssueMention() {
    }

    public IssueMention(UUID commentId, UUID userId, UUID mentionedBy, Instant createdAt) {
        this.commentId = commentId;
        this.userId = userId;
        this.mentionedBy = mentionedBy;
        this.createdAt = createdAt;
    }

    public UUID getCommentId() { return commentId; }
    public UUID getUserId() { return userId; }
}
