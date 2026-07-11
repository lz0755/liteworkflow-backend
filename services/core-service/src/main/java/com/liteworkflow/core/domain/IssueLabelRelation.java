package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "issue_label_relations")
public class IssueLabelRelation {

    @EmbeddedId
    private IssueLabelRelationId id;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected IssueLabelRelation() {
    }

    public IssueLabelRelation(UUID issueId, UUID labelId, UUID addedBy, Instant addedAt) {
        this.id = new IssueLabelRelationId(issueId, labelId);
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }

    public UUID getIssueId() {
        return id.issueId();
    }

    public UUID getLabelId() {
        return id.labelId();
    }
}
