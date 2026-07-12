package com.liteworkflow.core.export;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IssueExportRow(
        UUID id,
        long issueNumber,
        String title,
        String description,
        String state,
        List<String> assignees,
        List<String> labels,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt) {

    public IssueExportRow {
        assignees = List.copyOf(assignees);
        labels = List.copyOf(labels);
    }
}
