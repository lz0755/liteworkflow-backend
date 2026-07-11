package com.liteworkflow.core.dto.response;

import com.liteworkflow.core.domain.Issue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID projectId,
        long issueNumber,
        String title,
        String description,
        IssueStateResponse state,
        List<UUID> assigneeIds,
        List<IssueLabelResponse> labels,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static IssueResponse from(
            Issue issue,
            IssueStateResponse state,
            List<UUID> assigneeIds,
            List<IssueLabelResponse> labels) {
        return new IssueResponse(
                issue.getId(),
                issue.getProjectId(),
                issue.getIssueNumber(),
                issue.getTitle(),
                issue.getDescription(),
                state,
                List.copyOf(assigneeIds),
                List.copyOf(labels),
                issue.getCreatedBy(),
                issue.getUpdatedBy(),
                issue.getCreatedAt(),
                issue.getUpdatedAt());
    }
}
