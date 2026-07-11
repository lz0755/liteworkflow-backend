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
        name = "workspace_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workspace_members_workspace_user",
                columnNames = {"workspace_id", "user_id"}))
public class WorkspaceMember {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceRole role;

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

    protected WorkspaceMember() {
    }

    public WorkspaceMember(
            UUID id,
            UUID workspaceId,
            UUID userId,
            WorkspaceRole role,
            UUID addedBy,
            Instant now) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.status = MemberStatus.ACTIVE;
        this.addedBy = addedBy;
        this.joinedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void reactivate(WorkspaceRole role, UUID addedBy, Instant now) {
        this.role = role;
        this.status = MemberStatus.ACTIVE;
        this.addedBy = addedBy;
        this.joinedAt = now;
        this.removedAt = null;
        this.updatedAt = now;
    }

    public void changeRole(WorkspaceRole role, Instant now) {
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

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public WorkspaceRole getRole() {
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
