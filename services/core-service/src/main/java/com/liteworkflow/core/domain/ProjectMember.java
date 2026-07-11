package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        schema = "core",
        name = "project_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_project_members_project_user",
                columnNames = {"project_id", "user_id"}))
public class ProjectMember {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemberStatus status;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectMember() {
    }

    public ProjectMember(UUID id, UUID projectId, UUID userId, ProjectRole role, UUID addedBy, Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
        this.status = MemberStatus.ACTIVE;
        this.addedBy = addedBy;
        this.joinedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void reactivate(ProjectRole role, UUID addedBy, Instant now) {
        this.role = role;
        this.status = MemberStatus.ACTIVE;
        this.addedBy = addedBy;
        this.joinedAt = now;
        this.removedAt = null;
        this.updatedAt = now;
    }

    public void changeRole(ProjectRole role, Instant now) {
        this.role = role;
        this.updatedAt = now;
    }

    public void remove(Instant now) {
        this.status = MemberStatus.REMOVED;
        this.removedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ProjectRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public UUID getAddedBy() {
        return addedBy;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
