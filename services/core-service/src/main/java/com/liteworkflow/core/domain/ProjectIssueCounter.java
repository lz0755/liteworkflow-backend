package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "project_issue_counters")
public class ProjectIssueCounter {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "next_number", nullable = false)
    private long nextNumber;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectIssueCounter() {
    }

    public ProjectIssueCounter(UUID projectId, Instant now) {
        this.projectId = projectId;
        this.nextNumber = 1;
        this.updatedAt = now;
    }

    public long allocate(Instant now) {
        long allocated = nextNumber;
        nextNumber++;
        updatedAt = now;
        return allocated;
    }
}
