package com.liteworkflow.core.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.domain.IssueLabel;
import com.liteworkflow.core.domain.IssueLabelStatus;
import com.liteworkflow.core.domain.IssueState;
import com.liteworkflow.core.domain.IssueStateStatus;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.dto.request.CreateIssueLabelRequest;
import com.liteworkflow.core.dto.request.CreateIssueStateRequest;
import com.liteworkflow.core.dto.request.UpdateIssueLabelRequest;
import com.liteworkflow.core.dto.request.UpdateIssueStateRequest;
import com.liteworkflow.core.dto.response.IssueLabelResponse;
import com.liteworkflow.core.dto.response.IssueStateResponse;
import com.liteworkflow.core.outbox.ActivityOutboxService;
import com.liteworkflow.core.repository.IssueLabelRelationRepository;
import com.liteworkflow.core.repository.IssueLabelRepository;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.IssueStateRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueCatalogApplicationService {

    private final PermissionService permissionService;
    private final ProjectRepository projectRepository;
    private final IssueStateRepository stateRepository;
    private final IssueLabelRepository labelRepository;
    private final IssueRepository issueRepository;
    private final IssueLabelRelationRepository relationRepository;
    private final ActivityOutboxService activityOutboxService;
    private final Clock clock;

    public IssueCatalogApplicationService(
            PermissionService permissionService,
            ProjectRepository projectRepository,
            IssueStateRepository stateRepository,
            IssueLabelRepository labelRepository,
            IssueRepository issueRepository,
            IssueLabelRelationRepository relationRepository,
            ActivityOutboxService activityOutboxService,
            Clock clock) {
        this.permissionService = permissionService;
        this.projectRepository = projectRepository;
        this.stateRepository = stateRepository;
        this.labelRepository = labelRepository;
        this.issueRepository = issueRepository;
        this.relationRepository = relationRepository;
        this.activityOutboxService = activityOutboxService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IssueStateResponse> listStates(UUID actorId, UUID projectId) {
        permissionService.requireProjectMember(projectId, actorId);
        return stateRepository.findByProjectIdAndStatusOrderByPositionAsc(projectId, IssueStateStatus.ACTIVE)
                .stream().map(IssueStateResponse::from).toList();
    }

    @Transactional
    public IssueStateResponse createState(UUID actorId, UUID projectId, CreateIssueStateRequest request) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Project project = requireProject(projectId);
        String name = normalizeName(request.name());
        requireAvailableStateFields(projectId, null, name, request.position());
        Instant now = clock.instant();
        if (request.defaultState()) {
            clearCurrentDefault(projectId, null, now);
            stateRepository.flush();
        }
        IssueState state = stateRepository.save(new IssueState(
                UUID.randomUUID(),
                projectId,
                name,
                request.category(),
                request.position(),
                request.defaultState(),
                now));
        activityOutboxService.recordProjectChange(
                "issue.state.created",
                project.getWorkspaceId(),
                projectId,
                "ISSUE_STATE",
                state.getId(),
                actorId,
                Map.of("name", name, "category", request.category(), "position", request.position()));
        return IssueStateResponse.from(state);
    }

    @Transactional
    public IssueStateResponse updateState(
            UUID actorId, UUID projectId, UUID stateId, UpdateIssueStateRequest request) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Project project = requireProject(projectId);
        IssueState state = requireState(projectId, stateId);
        String name = request.name() == null ? state.getName() : normalizeName(request.name());
        var category = request.category() == null ? state.getCategory() : request.category();
        int position = request.position() == null ? state.getPosition() : request.position();
        boolean defaultState = request.defaultState() == null ? state.isDefaultState() : request.defaultState();
        if (state.isDefaultState() && !defaultState) {
            throw new BizException(CoreErrorCode.ISSUE_DEFAULT_STATE_REQUIRED);
        }
        if (name.equals(state.getName())
                && category == state.getCategory()
                && position == state.getPosition()
                && defaultState == state.isDefaultState()) {
            return IssueStateResponse.from(state);
        }
        requireAvailableStateFields(projectId, stateId, name, position);
        Instant now = clock.instant();
        if (defaultState && !state.isDefaultState()) {
            clearCurrentDefault(projectId, stateId, now);
            stateRepository.flush();
        }
        state.update(name, category, position, defaultState, now);
        activityOutboxService.recordProjectChange(
                "issue.state.updated",
                project.getWorkspaceId(),
                projectId,
                "ISSUE_STATE",
                stateId,
                actorId,
                Map.of("name", name, "category", category, "position", position, "defaultState", defaultState));
        return IssueStateResponse.from(state);
    }

    @Transactional
    public void deleteState(UUID actorId, UUID projectId, UUID stateId) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Project project = requireProject(projectId);
        IssueState state = requireState(projectId, stateId);
        if (state.isDefaultState()) {
            throw new BizException(CoreErrorCode.ISSUE_DEFAULT_STATE_REQUIRED);
        }
        if (issueRepository.existsByStateIdAndDeletedAtIsNull(stateId)) {
            throw new BizException(CoreErrorCode.ISSUE_STATE_IN_USE);
        }
        state.delete(clock.instant());
        activityOutboxService.recordProjectChange(
                "issue.state.deleted",
                project.getWorkspaceId(),
                projectId,
                "ISSUE_STATE",
                stateId,
                actorId,
                Map.of("name", state.getName()));
    }

    @Transactional(readOnly = true)
    public List<IssueLabelResponse> listLabels(UUID actorId, UUID projectId) {
        permissionService.requireProjectMember(projectId, actorId);
        return labelRepository.findByProjectIdAndStatusOrderByNameAscIdAsc(projectId, IssueLabelStatus.ACTIVE)
                .stream().map(IssueLabelResponse::from).toList();
    }

    @Transactional
    public IssueLabelResponse createLabel(UUID actorId, UUID projectId, CreateIssueLabelRequest request) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Project project = requireProject(projectId);
        String name = normalizeName(request.name());
        requireAvailableLabelName(projectId, null, name);
        IssueLabel label = labelRepository.save(new IssueLabel(
                UUID.randomUUID(), projectId, name, request.color().toUpperCase(java.util.Locale.ROOT), actorId,
                clock.instant()));
        activityOutboxService.recordProjectChange(
                "issue.label.created",
                project.getWorkspaceId(),
                projectId,
                "ISSUE_LABEL",
                label.getId(),
                actorId,
                Map.of("name", label.getName(), "color", label.getColor()));
        return IssueLabelResponse.from(label);
    }

    @Transactional
    public IssueLabelResponse updateLabel(
            UUID actorId, UUID projectId, UUID labelId, UpdateIssueLabelRequest request) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Project project = requireProject(projectId);
        IssueLabel label = requireLabel(projectId, labelId);
        String name = request.name() == null ? label.getName() : normalizeName(request.name());
        String color = request.color() == null
                ? label.getColor()
                : request.color().toUpperCase(java.util.Locale.ROOT);
        if (name.equals(label.getName()) && color.equals(label.getColor())) {
            return IssueLabelResponse.from(label);
        }
        requireAvailableLabelName(projectId, labelId, name);
        label.update(name, color, clock.instant());
        activityOutboxService.recordProjectChange(
                "issue.label.updated",
                project.getWorkspaceId(),
                projectId,
                "ISSUE_LABEL",
                labelId,
                actorId,
                Map.of("name", name, "color", color));
        return IssueLabelResponse.from(label);
    }

    @Transactional
    public void deleteLabel(UUID actorId, UUID projectId, UUID labelId) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Project project = requireProject(projectId);
        IssueLabel label = requireLabel(projectId, labelId);
        label.delete(clock.instant());
        activityOutboxService.recordProjectChange(
                "issue.label.deleted",
                project.getWorkspaceId(),
                projectId,
                "ISSUE_LABEL",
                labelId,
                actorId,
                Map.of("name", label.getName()));
    }

    private void clearCurrentDefault(UUID projectId, UUID exceptStateId, Instant now) {
        stateRepository.findByProjectIdAndDefaultStateTrueAndStatus(projectId, IssueStateStatus.ACTIVE)
                .filter(current -> exceptStateId == null || !current.getId().equals(exceptStateId))
                .ifPresent(current -> current.clearDefault(now));
    }

    private void requireAvailableStateFields(UUID projectId, UUID stateId, String name, int position) {
        boolean duplicateName = stateId == null
                ? stateRepository.existsByProjectIdAndStatusAndNameIgnoreCase(projectId, IssueStateStatus.ACTIVE, name)
                : stateRepository.existsByProjectIdAndStatusAndNameIgnoreCaseAndIdNot(
                        projectId, IssueStateStatus.ACTIVE, name, stateId);
        boolean duplicatePosition = stateId == null
                ? stateRepository.existsByProjectIdAndStatusAndPosition(projectId, IssueStateStatus.ACTIVE, position)
                : stateRepository.existsByProjectIdAndStatusAndPositionAndIdNot(
                        projectId, IssueStateStatus.ACTIVE, position, stateId);
        if (duplicateName || duplicatePosition) {
            throw new BizException(CoreErrorCode.ISSUE_STATE_INVALID, "Issue state name or position already exists");
        }
    }

    private void requireAvailableLabelName(UUID projectId, UUID labelId, String name) {
        boolean exists = labelId == null
                ? labelRepository.existsByProjectIdAndStatusAndNameIgnoreCase(projectId, IssueLabelStatus.ACTIVE, name)
                : labelRepository.existsByProjectIdAndStatusAndNameIgnoreCaseAndIdNot(
                        projectId, IssueLabelStatus.ACTIVE, name, labelId);
        if (exists) {
            throw new BizException(CoreErrorCode.ISSUE_LABEL_ALREADY_EXISTS);
        }
    }

    private IssueState requireState(UUID projectId, UUID stateId) {
        return stateRepository.findByIdAndProjectIdAndStatus(stateId, projectId, IssueStateStatus.ACTIVE)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_STATE_NOT_FOUND));
    }

    private IssueLabel requireLabel(UUID projectId, UUID labelId) {
        return labelRepository.findByIdAndProjectIdAndStatus(labelId, projectId, IssueLabelStatus.ACTIVE)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_LABEL_NOT_FOUND));
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Name is required");
        }
        return normalized;
    }
}
