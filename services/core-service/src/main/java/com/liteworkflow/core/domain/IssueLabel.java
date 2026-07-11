package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "issue_labels")
public class IssueLabel {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IssueLabelStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IssueLabel() {
    }

    public IssueLabel(UUID id, UUID projectId, String name, String color, UUID createdBy, Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.color = color;
        this.status = IssueLabelStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String color, Instant now) {
        this.name = name;
        this.color = color;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        status = IssueLabelStatus.DELETED;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public IssueLabelStatus getStatus() {
        return status;
    }
}
