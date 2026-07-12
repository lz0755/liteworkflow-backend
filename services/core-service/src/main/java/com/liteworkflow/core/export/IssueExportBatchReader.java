package com.liteworkflow.core.export;

import com.liteworkflow.core.domain.Issue;
import com.liteworkflow.core.domain.IssueAssignee;
import com.liteworkflow.core.domain.IssueLabel;
import com.liteworkflow.core.domain.IssueLabelRelation;
import com.liteworkflow.core.domain.IssueLabelStatus;
import com.liteworkflow.core.domain.IssueState;
import com.liteworkflow.core.repository.IssueAssigneeRepository;
import com.liteworkflow.core.repository.IssueLabelRelationRepository;
import com.liteworkflow.core.repository.IssueLabelRepository;
import com.liteworkflow.core.repository.IssueStateRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueExportBatchReader {

    private final IssueExportQueryRepository issues;
    private final IssueStateRepository states;
    private final IssueAssigneeRepository assignees;
    private final IssueLabelRelationRepository labelRelations;
    private final IssueLabelRepository labels;

    public IssueExportBatchReader(
            IssueExportQueryRepository issues,
            IssueStateRepository states,
            IssueAssigneeRepository assignees,
            IssueLabelRelationRepository labelRelations,
            IssueLabelRepository labels) {
        this.issues = issues;
        this.states = states;
        this.assignees = assignees;
        this.labelRelations = labelRelations;
        this.labels = labels;
    }

    @Transactional(readOnly = true)
    public long highWatermark(UUID projectId) {
        Long value = issues.findHighWatermark(projectId);
        return value == null ? 0 : value;
    }

    @Transactional(readOnly = true)
    public List<IssueExportRow> read(
            UUID projectId,
            long afterIssueNumber,
            long highWatermark,
            int batchSize) {
        List<Issue> batch = issues.findBatch(
                projectId, afterIssueNumber, highWatermark, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return List.of();

        Set<UUID> issueIds = new HashSet<>();
        Set<UUID> stateIds = new HashSet<>();
        batch.forEach(issue -> {
            issueIds.add(issue.getId());
            stateIds.add(issue.getStateId());
        });

        Map<UUID, String> stateNames = new HashMap<>();
        states.findByIdIn(stateIds).forEach(state -> stateNames.put(state.getId(), state.getName()));

        Map<UUID, List<String>> assigneeValues = new HashMap<>();
        assignees.findByIdIssueIdIn(issueIds).forEach(assignee -> assigneeValues
                .computeIfAbsent(assignee.getIssueId(), ignored -> new ArrayList<>())
                .add(assignee.getUserId().toString()));
        assigneeValues.values().forEach(values -> values.sort(String::compareTo));

        List<IssueLabelRelation> relations = labelRelations.findByIdIssueIdIn(issueIds);
        Set<UUID> labelIds = new HashSet<>();
        relations.forEach(relation -> labelIds.add(relation.getLabelId()));
        Map<UUID, IssueLabel> labelsById = new HashMap<>();
        labels.findAllById(labelIds).forEach(label -> labelsById.put(label.getId(), label));
        Map<UUID, List<String>> labelValues = new HashMap<>();
        relations.forEach(relation -> {
            IssueLabel label = labelsById.get(relation.getLabelId());
            if (label != null && label.getStatus() == IssueLabelStatus.ACTIVE) {
                labelValues.computeIfAbsent(relation.getIssueId(), ignored -> new ArrayList<>())
                        .add(label.getName());
            }
        });
        labelValues.values().forEach(values -> values.sort(String.CASE_INSENSITIVE_ORDER));

        return batch.stream()
                .sorted(Comparator.comparingLong(Issue::getIssueNumber))
                .map(issue -> new IssueExportRow(
                        issue.getId(),
                        issue.getIssueNumber(),
                        issue.getTitle(),
                        issue.getDescription(),
                        requiredState(stateNames, issue),
                        assigneeValues.getOrDefault(issue.getId(), List.of()),
                        labelValues.getOrDefault(issue.getId(), List.of()),
                        issue.getCreatedBy(),
                        issue.getUpdatedBy(),
                        issue.getCreatedAt(),
                        issue.getUpdatedAt()))
                .toList();
    }

    private String requiredState(Map<UUID, String> stateNames, Issue issue) {
        String state = stateNames.get(issue.getStateId());
        if (state == null) throw new IllegalStateException("Issue state is missing");
        return state;
    }
}
