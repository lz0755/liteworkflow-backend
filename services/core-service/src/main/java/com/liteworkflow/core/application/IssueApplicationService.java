package com.liteworkflow.core.application;

import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.domain.Issue;
import com.liteworkflow.core.domain.IssueAssignee;
import com.liteworkflow.core.domain.IssueLabel;
import com.liteworkflow.core.domain.IssueLabelRelation;
import com.liteworkflow.core.domain.IssueLabelStatus;
import com.liteworkflow.core.domain.IssueState;
import com.liteworkflow.core.domain.IssueStateStatus;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.dto.request.ChangeIssueStateRequest;
import com.liteworkflow.core.dto.request.CreateIssueRequest;
import com.liteworkflow.core.dto.request.UpdateIssueRequest;
import com.liteworkflow.core.dto.response.IssueLabelResponse;
import com.liteworkflow.core.dto.response.IssueResponse;
import com.liteworkflow.core.dto.response.IssueStateResponse;
import com.liteworkflow.core.outbox.ActivityOutboxService;
import com.liteworkflow.core.repository.IssueAssigneeRepository;
import com.liteworkflow.core.repository.IssueLabelRelationRepository;
import com.liteworkflow.core.repository.IssueLabelRepository;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.IssueStateRepository;
import com.liteworkflow.core.repository.ProjectIssueCounterRepository;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueApplicationService {

    private static final String ISSUE_AGGREGATE = "ISSUE";
    private static final int MAX_RELATIONS = 100;

    private final PermissionService permissionService;
    private final ProjectRepository projectRepository;
    private final ProjectIssueCounterRepository counterRepository;
    private final IssueRepository issueRepository;
    private final IssueStateRepository stateRepository;
    private final IssueLabelRepository labelRepository;
    private final IssueAssigneeRepository assigneeRepository;
    private final IssueLabelRelationRepository labelRelationRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ActivityOutboxService activityOutboxService;
    private final Clock clock;

    public IssueApplicationService(
            PermissionService permissionService,
            ProjectRepository projectRepository,
            ProjectIssueCounterRepository counterRepository,
            IssueRepository issueRepository,
            IssueStateRepository stateRepository,
            IssueLabelRepository labelRepository,
            IssueAssigneeRepository assigneeRepository,
            IssueLabelRelationRepository labelRelationRepository,
            ProjectMemberRepository projectMemberRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ActivityOutboxService activityOutboxService,
            Clock clock) {
        this.permissionService = permissionService;
        this.projectRepository = projectRepository;
        this.counterRepository = counterRepository;
        this.issueRepository = issueRepository;
        this.stateRepository = stateRepository;
        this.labelRepository = labelRepository;
        this.assigneeRepository = assigneeRepository;
        this.labelRelationRepository = labelRelationRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.activityOutboxService = activityOutboxService;
        this.clock = clock;
    }

    @Transactional
    public IssueResponse create(UUID actorId, UUID projectId, CreateIssueRequest request) {
        permissionService.requireIssueWriter(projectId, actorId);
        Project project = requireProject(projectId);
        String title = normalizeTitle(request.title());
        String description = normalizeDescription(request.description());
        Set<UUID> assigneeIds = normalizeIds(request.assigneeIds());
        Set<UUID> labelIds = normalizeIds(request.labelIds());
        validateRelationCount(assigneeIds, labelIds);

        // This row lock serializes allocation and the second idempotency check for one project.
        var counter = counterRepository.findByProjectIdForUpdate(projectId)
                .orElseThrow(() -> new IllegalStateException("Project issue counter is missing"));
        Issue existing = findIdempotent(projectId, request.clientRequestId());
        if (existing != null) {
            requireSameCreate(existing, title, description, request.stateId(), assigneeIds, labelIds);
            return assemble(List.of(existing)).getFirst();
        }

        IssueState state = requireState(projectId, request.stateId());
        validateAssignees(project, assigneeIds);
        List<IssueLabel> labels = requireLabels(projectId, labelIds);

        Instant now = clock.instant();
        Issue issue = issueRepository.save(new Issue(
                UUID.randomUUID(),
                projectId,
                counter.allocate(now),
                title,
                description,
                state.getId(),
                request.clientRequestId(),
                actorId,
                now));
        assigneeRepository.saveAll(assigneeIds.stream()
                .map(userId -> new IssueAssignee(issue.getId(), userId, actorId, now))
                .toList());
        labelRelationRepository.saveAll(labels.stream()
                .map(label -> new IssueLabelRelation(issue.getId(), label.getId(), actorId, now))
                .toList());
        activityOutboxService.recordProjectChange(
                "issue.created",
                project.getWorkspaceId(),
                projectId,
                ISSUE_AGGREGATE,
                issue.getId(),
                actorId,
                issuePayload(issue, Map.of("assigneeIds", assigneeIds, "labelIds", labelIds)));
        return assemble(List.of(issue)).getFirst();
    }

    @Transactional(readOnly = true)
    public PageResult<IssueResponse> list(
            UUID actorId,
            UUID projectId,
            String keyword,
            UUID stateId,
            UUID assigneeId,
            UUID labelId,
            UUID createdBy,
            int page,
            int size) {
        permissionService.requireProjectMember(projectId, actorId);
        validatePage(page, size);
        if (stateId != null) {
            requireState(projectId, stateId);
        }
        Specification<Issue> specification = issueSpecification(
                projectId, normalizeKeyword(keyword), stateId, assigneeId, labelId, createdBy);
        var issues = issueRepository.findAll(
                specification,
                PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"))));
        return PageResult.of(assemble(issues.getContent()), issues.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public IssueResponse get(UUID actorId, UUID issueId) {
        UUID projectId = requireIssueProject(issueId);
        permissionService.requireProjectMember(projectId, actorId);
        return assemble(List.of(requireIssue(issueId))).getFirst();
    }

    @Transactional
    public IssueResponse update(UUID actorId, UUID issueId, UpdateIssueRequest request) {
        UUID projectId = requireIssueProject(issueId);
        permissionService.requireIssueWriter(projectId, actorId);
        Issue issue = lockIssue(issueId);
        String title = request.title() == null ? issue.getTitle() : normalizeTitle(request.title());
        String description = request.description() == null
                ? issue.getDescription()
                : normalizeDescription(request.description());
        if (title.equals(issue.getTitle()) && java.util.Objects.equals(description, issue.getDescription())) {
            return assemble(List.of(issue)).getFirst();
        }
        issue.update(title, description, actorId, clock.instant());
        Project project = requireProject(projectId);
        activityOutboxService.recordProjectChange(
                "issue.updated",
                project.getWorkspaceId(),
                projectId,
                ISSUE_AGGREGATE,
                issueId,
                actorId,
                issuePayload(issue, Map.of()));
        return assemble(List.of(issue)).getFirst();
    }

    @Transactional
    public IssueResponse changeState(UUID actorId, UUID issueId, ChangeIssueStateRequest request) {
        UUID projectId = requireIssueProject(issueId);
        permissionService.requireIssueWriter(projectId, actorId);
        IssueState state = requireState(projectId, request.stateId());
        Issue issue = lockIssue(issueId);
        if (issue.getStateId().equals(state.getId())) {
            return assemble(List.of(issue)).getFirst();
        }
        UUID previousStateId = issue.getStateId();
        issue.moveTo(state.getId(), actorId, clock.instant());
        Project project = requireProject(projectId);
        activityOutboxService.recordProjectChange(
                "issue.state.changed",
                project.getWorkspaceId(),
                projectId,
                ISSUE_AGGREGATE,
                issueId,
                actorId,
                issuePayload(issue, Map.of("previousStateId", previousStateId)));
        return assemble(List.of(issue)).getFirst();
    }

    @Transactional
    public IssueResponse replaceAssignees(UUID actorId, UUID issueId, Set<UUID> requestedIds) {
        UUID projectId = requireIssueProject(issueId);
        permissionService.requireIssueWriter(projectId, actorId);
        Issue issue = lockIssue(issueId);
        Project project = requireProject(projectId);
        Set<UUID> assigneeIds = normalizeIds(requestedIds);
        validateRelationCount(assigneeIds, Set.of());
        validateAssignees(project, assigneeIds);
        Set<UUID> existingIds = assigneeRepository.findByIdIssueId(issueId).stream()
                .map(IssueAssignee::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        if (existingIds.equals(assigneeIds)) {
            return assemble(List.of(issue)).getFirst();
        }
        Instant now = clock.instant();
        assigneeRepository.deleteByIssueId(issueId);
        assigneeRepository.saveAll(assigneeIds.stream()
                .map(userId -> new IssueAssignee(issueId, userId, actorId, now))
                .toList());
        issue.touch(actorId, now);
        activityOutboxService.recordProjectChange(
                "issue.assignees.changed",
                project.getWorkspaceId(),
                projectId,
                ISSUE_AGGREGATE,
                issueId,
                actorId,
                issuePayload(issue, Map.of("previousAssigneeIds", existingIds, "assigneeIds", assigneeIds)));
        return assemble(List.of(issue)).getFirst();
    }

    @Transactional
    public IssueResponse replaceLabels(UUID actorId, UUID issueId, Set<UUID> requestedIds) {
        UUID projectId = requireIssueProject(issueId);
        permissionService.requireIssueWriter(projectId, actorId);
        Issue issue = lockIssue(issueId);
        Project project = requireProject(projectId);
        Set<UUID> labelIds = normalizeIds(requestedIds);
        validateRelationCount(Set.of(), labelIds);
        List<IssueLabel> labels = requireLabels(projectId, labelIds);
        Set<UUID> existingIds = labelRelationRepository.findByIdIssueId(issueId).stream()
                .map(IssueLabelRelation::getLabelId)
                .collect(java.util.stream.Collectors.toSet());
        if (existingIds.equals(labelIds)) {
            return assemble(List.of(issue)).getFirst();
        }
        Instant now = clock.instant();
        labelRelationRepository.deleteByIssueId(issueId);
        labelRelationRepository.saveAll(labels.stream()
                .map(label -> new IssueLabelRelation(issueId, label.getId(), actorId, now))
                .toList());
        issue.touch(actorId, now);
        activityOutboxService.recordProjectChange(
                "issue.labels.changed",
                project.getWorkspaceId(),
                projectId,
                ISSUE_AGGREGATE,
                issueId,
                actorId,
                issuePayload(issue, Map.of("previousLabelIds", existingIds, "labelIds", labelIds)));
        return assemble(List.of(issue)).getFirst();
    }

    @Transactional
    public void delete(UUID actorId, UUID issueId) {
        UUID projectId = requireIssueProject(issueId);
        permissionService.requireIssueWriter(projectId, actorId);
        Issue issue = lockIssue(issueId);
        Instant now = clock.instant();
        issue.delete(actorId, now);
        Project project = requireProject(projectId);
        activityOutboxService.recordProjectChange(
                "issue.deleted",
                project.getWorkspaceId(),
                projectId,
                ISSUE_AGGREGATE,
                issueId,
                actorId,
                issuePayload(issue, Map.of()));
    }

    private Specification<Issue> issueSpecification(
            UUID projectId,
            String keyword,
            UUID stateId,
            UUID assigneeId,
            UUID labelId,
            UUID createdBy) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("projectId"), projectId));
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (keyword != null) {
                predicates.add(builder.like(builder.lower(root.get("title")), "%" + keyword + "%"));
            }
            if (stateId != null) {
                predicates.add(builder.equal(root.get("stateId"), stateId));
            }
            if (createdBy != null) {
                predicates.add(builder.equal(root.get("createdBy"), createdBy));
            }
            if (assigneeId != null) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<IssueAssignee> assignment = subquery.from(IssueAssignee.class);
                subquery.select(builder.literal(1)).where(
                        builder.equal(assignment.get("id").get("issueId"), root.get("id")),
                        builder.equal(assignment.get("id").get("userId"), assigneeId));
                predicates.add(builder.exists(subquery));
            }
            if (labelId != null) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<IssueLabelRelation> relation = subquery.from(IssueLabelRelation.class);
                subquery.select(builder.literal(1)).where(
                        builder.equal(relation.get("id").get("issueId"), root.get("id")),
                        builder.equal(relation.get("id").get("labelId"), labelId));
                predicates.add(builder.exists(subquery));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<IssueResponse> assemble(List<Issue> issues) {
        if (issues.isEmpty()) {
            return List.of();
        }
        Set<UUID> issueIds = issues.stream().map(Issue::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> stateIds = issues.stream().map(Issue::getStateId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, IssueState> states = new HashMap<>();
        stateRepository.findByIdIn(stateIds).forEach(state -> states.put(state.getId(), state));

        Map<UUID, List<UUID>> assignees = new HashMap<>();
        assigneeRepository.findByIdIssueIdIn(issueIds).forEach(assignment ->
                assignees.computeIfAbsent(assignment.getIssueId(), ignored -> new ArrayList<>())
                        .add(assignment.getUserId()));
        assignees.values().forEach(ids -> ids.sort(Comparator.comparing(UUID::toString)));

        List<IssueLabelRelation> relations = labelRelationRepository.findByIdIssueIdIn(issueIds);
        Set<UUID> labelIds = relations.stream()
                .map(IssueLabelRelation::getLabelId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, IssueLabel> labelsById = new HashMap<>();
        labelRepository.findAllById(labelIds).forEach(label -> labelsById.put(label.getId(), label));
        Map<UUID, List<IssueLabelResponse>> labels = new HashMap<>();
        relations.forEach(relation -> {
            IssueLabel label = labelsById.get(relation.getLabelId());
            if (label != null && label.getStatus() == IssueLabelStatus.ACTIVE) {
                labels.computeIfAbsent(relation.getIssueId(), ignored -> new ArrayList<>())
                        .add(IssueLabelResponse.from(label));
            }
        });
        labels.values().forEach(values -> values.sort(
                Comparator.comparing(IssueLabelResponse::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(value -> value.id().toString())));

        return issues.stream().map(issue -> {
            IssueState state = states.get(issue.getStateId());
            if (state == null) {
                throw new IllegalStateException("Issue state is missing");
            }
            return IssueResponse.from(
                    issue,
                    IssueStateResponse.from(state),
                    assignees.getOrDefault(issue.getId(), List.of()),
                    labels.getOrDefault(issue.getId(), List.of()));
        }).toList();
    }

    private void validateAssignees(Project project, Set<UUID> requestedIds) {
        if (requestedIds.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(requestedIds);
        Set<UUID> eligible = new HashSet<>(projectMemberRepository.findEligibleAssigneeIds(project.getId(), ids));
        eligible.addAll(workspaceMemberRepository.findActiveManagerIds(project.getWorkspaceId(), ids));
        if (!eligible.equals(requestedIds)) {
            throw new BizException(CoreErrorCode.ISSUE_ASSIGNEE_NOT_ELIGIBLE);
        }
    }

    private List<IssueLabel> requireLabels(UUID projectId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<IssueLabel> labels = labelRepository.findActiveByProjectIdAndIdIn(projectId, ids);
        if (labels.size() != ids.size()) {
            throw new BizException(CoreErrorCode.ISSUE_LABEL_NOT_FOUND);
        }
        return labels;
    }

    private IssueState requireState(UUID projectId, UUID requestedId) {
        if (requestedId == null) {
            return stateRepository.findByProjectIdAndDefaultStateTrueAndStatus(projectId, IssueStateStatus.ACTIVE)
                    .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_DEFAULT_STATE_REQUIRED));
        }
        return stateRepository.findByIdAndProjectIdAndStatus(requestedId, projectId, IssueStateStatus.ACTIVE)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_STATE_INVALID));
    }

    private Issue findIdempotent(UUID projectId, UUID requestId) {
        if (requestId == null) {
            return null;
        }
        Issue issue = issueRepository.findByProjectIdAndClientRequestId(projectId, requestId).orElse(null);
        if (issue != null && issue.isDeleted()) {
            throw new BizException(CoreErrorCode.ISSUE_IDEMPOTENCY_CONFLICT);
        }
        return issue;
    }

    private void requireSameCreate(
            Issue issue,
            String title,
            String description,
            UUID stateId,
            Set<UUID> assigneeIds,
            Set<UUID> labelIds) {
        Set<UUID> existingAssignees = assigneeRepository.findByIdIssueId(issue.getId()).stream()
                .map(IssueAssignee::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> existingLabels = labelRelationRepository.findByIdIssueId(issue.getId()).stream()
                .map(IssueLabelRelation::getLabelId)
                .collect(java.util.stream.Collectors.toSet());
        if (!issue.getTitle().equals(title)
                || !java.util.Objects.equals(issue.getDescription(), description)
                || (stateId != null && !issue.getStateId().equals(stateId))
                || !existingAssignees.equals(assigneeIds)
                || !existingLabels.equals(labelIds)) {
            throw new BizException(CoreErrorCode.ISSUE_IDEMPOTENCY_CONFLICT);
        }
    }

    private Map<String, Object> issuePayload(Issue issue, Map<String, ?> extras) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issueNumber", issue.getIssueNumber());
        payload.put("title", issue.getTitle());
        payload.put("stateId", issue.getStateId());
        payload.putAll(extras);
        return payload;
    }

    private UUID requireIssueProject(UUID issueId) {
        return issueRepository.findActiveProjectId(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
    }

    private Issue requireIssue(UUID issueId) {
        return issueRepository.findActiveById(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
    }

    private Issue lockIssue(UUID issueId) {
        return issueRepository.findActiveByIdForUpdate(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
    }

    private String normalizeTitle(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 240) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Issue title is required");
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private Set<UUID> normalizeIds(Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        if (values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Relation ids must not contain null");
        }
        return Set.copyOf(values);
    }

    private void validateRelationCount(Set<UUID> assigneeIds, Set<UUID> labelIds) {
        if (assigneeIds.size() > MAX_RELATIONS || labelIds.size() > MAX_RELATIONS) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Too many issue relations");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Invalid pagination");
        }
    }
}
