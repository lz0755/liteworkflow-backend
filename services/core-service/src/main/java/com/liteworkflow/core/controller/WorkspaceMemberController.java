package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.application.WorkspaceMemberApplicationService;
import com.liteworkflow.core.dto.request.AddWorkspaceMemberRequest;
import com.liteworkflow.core.dto.request.UpdateWorkspaceMemberRequest;
import com.liteworkflow.core.dto.response.WorkspaceMemberResponse;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {

    private final WorkspaceMemberApplicationService memberService;

    public WorkspaceMemberController(WorkspaceMemberApplicationService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<PageResult<WorkspaceMemberResponse>> list(
            CurrentUser currentUser,
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(memberService.list(currentUser.userId(), workspaceId, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkspaceMemberResponse> add(
            CurrentUser currentUser,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request) {
        return ApiResponse.success(memberService.add(
                currentUser.userId(), workspaceId, request.userId(), request.role()));
    }

    @PatchMapping("/{userId}")
    public ApiResponse<WorkspaceMemberResponse> changeRole(
            CurrentUser currentUser,
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateWorkspaceMemberRequest request) {
        return ApiResponse.success(memberService.changeRole(
                currentUser.userId(), workspaceId, userId, request.role()));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> remove(
            CurrentUser currentUser,
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId) {
        memberService.remove(currentUser.userId(), workspaceId, userId);
        return ApiResponse.success();
    }
}
