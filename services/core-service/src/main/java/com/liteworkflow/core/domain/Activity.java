package com.liteworkflow.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "core", name = "activities")
public class Activity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "activity_type", nullable = false, length = 128)
    private String activityType;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private JsonNode payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Activity() {
    }

    public Activity(
            UUID id,
            UUID workspaceId,
            UUID actorId,
            String activityType,
            String aggregateType,
            UUID aggregateId,
            JsonNode payloadJson,
            Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.actorId = actorId;
        this.activityType = activityType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
    }

    public Activity(
            UUID id,
            UUID workspaceId,
            UUID projectId,
            UUID actorId,
            String activityType,
            String aggregateType,
            UUID aggregateId,
            JsonNode payloadJson,
            Instant createdAt) {
        this(id, workspaceId, actorId, activityType, aggregateType, aggregateId, payloadJson, createdAt);
        this.projectId = projectId;
    }

    public String getActivityType() {
        return activityType;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getProjectId() {
        return projectId;
    }
}
