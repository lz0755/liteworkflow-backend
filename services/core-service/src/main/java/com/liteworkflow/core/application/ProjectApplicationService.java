package com.liteworkflow.core.application;

import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.domain.IssueState;
import com.liteworkflow.core.domain.IssueStateCategory;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.domain.ProjectMember;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.Workspace;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.dto.request.CreateProjectRequest;
import com.liteworkflow.core.dto.request.UpdateProjectRequest;
import com.liteworkflow.core.dto.response.ProjectResponse;
import com.liteworkflow.core.outbox.ActivityOutboxService;
import com.liteworkflow.core.outbox.ProjectMemberEventPayload;
import com.liteworkflow.core.repository.IssueStateRepository;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import com.liteworkflow.core.repository.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectApplicationService {

    private final PermissionService permissionService;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final IssueStateRepository issueStateRepository;
    private final ActivityOutboxService activityOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public ProjectApplicationService(
            PermissionService permissionService,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            IssueStateRepository issueStateRepository,
            ActivityOutboxService activityOutboxService,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.permissionService = permissionService;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.issueStateRepository = issueStateRepository;
        this.activityOutboxService = activityOutboxService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ProjectResponse create(UUID actorId, UUID workspaceId, CreateProjectRequest request) {
        permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        lockWorkspace(workspaceId);
        permissionService.requireWorkspaceMemberManager(workspaceId, actorId);
        Instant now = clock.instant();
        Project project = projectRepository.save(new Project(
                UUID.randomUUID(),
                workspaceId,
                normalizeName(request.name()),
                normalizeDescription(request.description()),
                actorId,
                now));
        ProjectMember creator = memberRepository.save(new ProjectMember(
                UUID.randomUUID(), project.getId(), actorId, ProjectRole.PROJECT_ADMIN, actorId, now));
        issueStateRepository.saveAll(List.of(
                new IssueState(UUID.randomUUID(), project.getId(), "To Do", IssueStateCategory.TODO, 0, true, now),
                new IssueState(
                        UUID.randomUUID(), project.getId(), "In Progress", IssueStateCategory.IN_PROGRESS, 1, false, now),
                new IssueState(UUID.randomUUID(), project.getId(), "Done", IssueStateCategory.DONE, 2, false, now)));
        activityOutboxService.recordProjectMemberChange(
                "project.member.added",
                workspaceId,
                project.getId(),
                creator.getId(),
                actorId,
                new ProjectMemberEventPayload(actorId, ProjectRole.PROJECT_ADMIN, null));
        applicationEventPublisher.publishEvent(new ProjectPermissionInvalidation(project.getId(), actorId));
        return ProjectResponse.from(project, ProjectRole.PROJECT_ADMIN, true);
    }

    @Transactional(readOnly = true)
    public PageResult<ProjectResponse> list(UUID actorId, UUID workspaceId, int page, int size) {
        validatePage(page, size);
        WorkspaceRole workspaceRole = permissionService.requireWorkspaceMember(workspaceId, actorId);
        boolean workspaceAdmin = workspaceRole.canManageMembers();
        var projects = projectRepository.findAccessible(
                workspaceId, actorId, workspaceAdmin, PageRequest.of(page - 1, size));
        List<ProjectResponse> records = projects.getContent().stream()
                .map(project -> ProjectResponse.from(
                        project,
                        permissionService.requireProjectMember(project.getId(), actorId),
                        workspaceAdmin))
                .toList();
        return PageResult.of(records, projects.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID actorId, UUID projectId) {
        ProjectRole role = permissionService.requireProjectMember(projectId, actorId);
        Project project = requireProject(projectId);
        return ProjectResponse.from(project, role, isWorkspaceAdmin(project.getWorkspaceId(), actorId));
    }

    @Transactional
    public ProjectResponse update(UUID actorId, UUID projectId, UpdateProjectRequest request) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Project existing = requireProject(projectId);
        lockWorkspace(existing.getWorkspaceId());
        Project project = lockProject(projectId);
        ProjectRole role = permissionService.requireProjectMemberManager(projectId, actorId);
        String name = request.name() == null ? project.getName() : normalizeName(request.name());
        String description = request.description() == null
                ? project.getDescription()
                : normalizeDescription(request.description());
        project.update(name, description, clock.instant());
        return ProjectResponse.from(project, role, isWorkspaceAdmin(project.getWorkspaceId(), actorId));
    }

    @Transactional
    public void delete(UUID actorId, UUID projectId) {
        permissionService.requireProjectMemberManager(projectId, actorId);
        Project existing = requireProject(projectId);
        lockWorkspace(existing.getWorkspaceId());
        Project project = lockProject(projectId);
        permissionService.requireProjectMemberManager(projectId, actorId);
        var affectedUsers = new HashSet<>(memberRepository.findActiveUserIdsByProjectId(projectId));
        affectedUsers.addAll(workspaceMemberRepository.findActiveUserIdsByWorkspaceId(project.getWorkspaceId()));
        project.delete(clock.instant());
        affectedUsers.forEach(userId -> applicationEventPublisher.publishEvent(
                new ProjectPermissionInvalidation(projectId, userId)));
    }

    private Workspace lockWorkspace(UUID workspaceId) {
        return workspaceRepository.findActiveByIdForUpdate(workspaceId)
                .orElseThrow(() -> new BizException(CoreErrorCode.WORKSPACE_NOT_FOUND));
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
    }

    private Project lockProject(UUID projectId) {
        return projectRepository.findActiveByIdForUpdate(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
    }

    private boolean isWorkspaceAdmin(UUID workspaceId, UUID actorId) {
        return permissionService.requireWorkspaceMember(workspaceId, actorId).canManageMembers();
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Project name is required");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Invalid pagination");
        }
    }
}
