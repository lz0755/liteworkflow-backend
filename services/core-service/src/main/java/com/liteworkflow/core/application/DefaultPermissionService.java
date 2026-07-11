package com.liteworkflow.core.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.domain.WorkspaceRole;
import com.liteworkflow.core.repository.ProjectMemberRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import com.liteworkflow.core.repository.WorkspaceMemberRepository;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultPermissionService implements PermissionService {

    private final UserDirectoryRepository userDirectoryRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspacePermissionCache permissionCache;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectPermissionCache projectPermissionCache;

    public DefaultPermissionService(
            UserDirectoryRepository userDirectoryRepository,
            WorkspaceMemberRepository memberRepository,
            WorkspacePermissionCache permissionCache,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ProjectPermissionCache projectPermissionCache) {
        this.userDirectoryRepository = userDirectoryRepository;
        this.memberRepository = memberRepository;
        this.permissionCache = permissionCache;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectPermissionCache = projectPermissionCache;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveUser(UUID userId) {
        UserDirectory user = userDirectoryRepository.findById(userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.USER_NOT_FOUND));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BizException(CoreErrorCode.USER_NOT_ACTIVE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRole requireWorkspaceMember(UUID workspaceId, UUID userId) {
        requireActiveUser(userId);
        // The database remains authoritative. A failed Redis eviction must never preserve access.
        WorkspaceRole role = memberRepository.findActiveRole(workspaceId, userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.WORKSPACE_PERMISSION_DENIED));
        permissionCache.put(workspaceId, userId, role);
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRole requireWorkspaceRole(UUID workspaceId, UUID userId, WorkspaceRole... roles) {
        WorkspaceRole currentRole = requireWorkspaceMember(workspaceId, userId);
        if (Arrays.stream(roles).noneMatch(currentRole::equals)) {
            throw new BizException(CoreErrorCode.WORKSPACE_MEMBER_PERMISSION_DENIED);
        }
        return currentRole;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectRole requireProjectMember(UUID projectId, UUID userId) {
        requireActiveUser(userId);
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
        WorkspaceRole workspaceRole = memberRepository.findActiveRole(project.getWorkspaceId(), userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_PERMISSION_DENIED));
        ProjectRole role;
        if (workspaceRole.canManageMembers()) {
            role = ProjectRole.PROJECT_ADMIN;
        } else {
            role = projectMemberRepository.findActiveRole(projectId, userId)
                    .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_PERMISSION_DENIED));
        }
        projectPermissionCache.put(projectId, userId, role);
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectRole requireProjectRole(UUID projectId, UUID userId, ProjectRole... roles) {
        ProjectRole currentRole = requireProjectMember(projectId, userId);
        if (Arrays.stream(roles).noneMatch(currentRole::equals)) {
            throw new BizException(CoreErrorCode.PROJECT_MEMBER_PERMISSION_DENIED);
        }
        return currentRole;
    }

    @Override
    public boolean canReadProject(UUID projectId, UUID userId) {
        try {
            requireProjectMember(projectId, userId);
            return true;
        } catch (BizException exception) {
            return false;
        }
    }

    @Override
    public boolean canManageProjectMembers(UUID projectId, UUID userId) {
        try {
            requireProjectMemberManager(projectId, userId);
            return true;
        } catch (BizException exception) {
            return false;
        }
    }
}
