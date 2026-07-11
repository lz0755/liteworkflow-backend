package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.application.WorkspaceApplicationService;
import com.liteworkflow.core.dto.request.CreateWorkspaceRequest;
import com.liteworkflow.core.dto.request.UpdateWorkspaceRequest;
import com.liteworkflow.core.dto.response.WorkspaceResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceApplicationService workspaceService;

    public WorkspaceController(WorkspaceApplicationService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ApiResponse<PageResult<WorkspaceResponse>> list(
            CurrentUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(workspaceService.list(currentUser.userId(), page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkspaceResponse> create(
            CurrentUser currentUser, @Valid @RequestBody CreateWorkspaceRequest request) {
        return ApiResponse.success(workspaceService.create(currentUser.userId(), request));
    }

    @GetMapping("/{workspaceId}")
    public ApiResponse<WorkspaceResponse> get(
            CurrentUser currentUser, @PathVariable UUID workspaceId) {
        return ApiResponse.success(workspaceService.get(currentUser.userId(), workspaceId));
    }

    @PatchMapping("/{workspaceId}")
    public ApiResponse<WorkspaceResponse> update(
            CurrentUser currentUser,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        return ApiResponse.success(workspaceService.update(currentUser.userId(), workspaceId, request));
    }

    @DeleteMapping("/{workspaceId}")
    public ApiResponse<Void> delete(CurrentUser currentUser, @PathVariable UUID workspaceId) {
        workspaceService.delete(currentUser.userId(), workspaceId);
        return ApiResponse.success();
    }
}
