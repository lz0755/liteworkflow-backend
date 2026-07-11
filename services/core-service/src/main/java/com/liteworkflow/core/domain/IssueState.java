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
@Table(schema = "core", name = "issue_states")
public class IssueState {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IssueStateCategory category;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_default", nullable = false)
    private boolean defaultState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IssueStateStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IssueState() {
    }

    public IssueState(
            UUID id,
            UUID projectId,
            String name,
            IssueStateCategory category,
            int position,
            boolean defaultState,
            Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.category = category;
        this.position = position;
        this.defaultState = defaultState;
        this.status = IssueStateStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
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

    public IssueStateCategory getCategory() {
        return category;
    }

    public int getPosition() {
        return position;
    }

    public boolean isDefaultState() {
        return defaultState;
    }

    public IssueStateStatus getStatus() {
        return status;
    }

    public void update(String name, IssueStateCategory category, int position, boolean defaultState, Instant now) {
        this.name = name;
        this.category = category;
        this.position = position;
        this.defaultState = defaultState;
        this.updatedAt = now;
    }

    public void clearDefault(Instant now) {
        this.defaultState = false;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.status = IssueStateStatus.DELETED;
        this.defaultState = false;
        this.updatedAt = now;
    }
}
