package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.application.CoreErrorCode;
import com.liteworkflow.core.application.PermissionService;
import com.liteworkflow.core.config.CoreProperties;
import com.liteworkflow.core.domain.Project;
import com.liteworkflow.core.repository.IssueRepository;
import com.liteworkflow.core.repository.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal authorization oracle used by infra-service; this path is never exposed by the gateway. */
@RestController
public class InternalFileAccessController {

    private final PermissionService permissions;
    private final ProjectRepository projects;
    private final IssueRepository issues;
    private final CoreProperties properties;

    public InternalFileAccessController(
            PermissionService permissions,
            ProjectRepository projects,
            IssueRepository issues,
            CoreProperties properties) {
        this.permissions = permissions;
        this.projects = projects;
        this.issues = issues;
        this.properties = properties;
    }

    @GetMapping("/internal/v1/file-access/{scope}/{resourceId}")
    public ApiResponse<FileAccessContext> authorize(
            @RequestHeader("X-Internal-Token") String internalToken,
            @PathVariable String scope,
            @PathVariable UUID resourceId,
            @RequestParam UUID userId,
            @RequestParam String action) {
        requireInternalToken(internalToken);
        boolean write = "WRITE".equalsIgnoreCase(action);
        if (!write && !"READ".equalsIgnoreCase(action)) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Unsupported file access action");
        }

        return ApiResponse.success(switch (scope.toUpperCase(Locale.ROOT)) {
            case "WORKSPACE" -> authorizeWorkspace(resourceId, userId, write);
            case "PROJECT" -> authorizeProject(resourceId, userId, write);
            case "ISSUE" -> authorizeIssue(resourceId, userId, write);
            default -> throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Unsupported file access scope");
        });
    }

    private FileAccessContext authorizeWorkspace(UUID workspaceId, UUID userId, boolean write) {
        if (write) {
            permissions.requireWorkspaceMemberManager(workspaceId, userId);
        } else {
            permissions.requireWorkspaceMember(workspaceId, userId);
        }
        return new FileAccessContext(workspaceId, null, null);
    }

    private FileAccessContext authorizeProject(UUID projectId, UUID userId, boolean write) {
        Project project = projects.findActiveById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
        if (write) {
            permissions.requireProjectMemberManager(projectId, userId);
        } else {
            permissions.requireProjectMember(projectId, userId);
        }
        return new FileAccessContext(project.getWorkspaceId(), projectId, null);
    }

    private FileAccessContext authorizeIssue(UUID issueId, UUID userId, boolean write) {
        UUID projectId = issues.findActiveProjectId(issueId)
                .orElseThrow(() -> new BizException(CoreErrorCode.ISSUE_NOT_FOUND));
        Project project = projects.findActiveById(projectId)
                .orElseThrow(() -> new BizException(CoreErrorCode.PROJECT_NOT_FOUND));
        if (write) {
            permissions.requireIssueWriter(projectId, userId);
        } else {
            permissions.requireProjectMember(projectId, userId);
        }
        return new FileAccessContext(project.getWorkspaceId(), projectId, issueId);
    }

    private void requireInternalToken(String actual) {
        byte[] expectedBytes = properties.getInternalToken().getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "Invalid internal service credential");
        }
    }

    public record FileAccessContext(UUID workspaceId, UUID projectId, UUID issueId) {
    }
}
