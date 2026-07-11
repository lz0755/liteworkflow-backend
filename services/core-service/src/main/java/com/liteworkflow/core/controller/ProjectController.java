package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.application.ProjectApplicationService;
import com.liteworkflow.core.dto.request.CreateProjectRequest;
import com.liteworkflow.core.dto.request.UpdateProjectRequest;
import com.liteworkflow.core.dto.response.ProjectResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController {

    private final ProjectApplicationService projectService;

    public ProjectController(ProjectApplicationService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects")
    public ApiResponse<PageResult<ProjectResponse>> list(
            CurrentUser currentUser,
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(projectService.list(currentUser.userId(), workspaceId, page, size));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(
            CurrentUser currentUser,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.success(projectService.create(currentUser.userId(), workspaceId, request));
    }

    @GetMapping("/api/v1/projects/{projectId}")
    public ApiResponse<ProjectResponse> get(CurrentUser currentUser, @PathVariable UUID projectId) {
        return ApiResponse.success(projectService.get(currentUser.userId(), projectId));
    }

    @PatchMapping("/api/v1/projects/{projectId}")
    public ApiResponse<ProjectResponse> update(
            CurrentUser currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ApiResponse.success(projectService.update(currentUser.userId(), projectId, request));
    }

    @DeleteMapping("/api/v1/projects/{projectId}")
    public ApiResponse<Void> delete(CurrentUser currentUser, @PathVariable UUID projectId) {
        projectService.delete(currentUser.userId(), projectId);
        return ApiResponse.success();
    }
}
