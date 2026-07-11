package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.application.ProjectMemberApplicationService;
import com.liteworkflow.core.dto.request.AddProjectMemberRequest;
import com.liteworkflow.core.dto.request.UpdateProjectMemberRequest;
import com.liteworkflow.core.dto.response.ProjectMemberResponse;
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
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberApplicationService memberService;

    public ProjectMemberController(ProjectMemberApplicationService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<PageResult<ProjectMemberResponse>> list(
            CurrentUser currentUser,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(memberService.list(currentUser.userId(), projectId, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectMemberResponse> add(
            CurrentUser currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectMemberRequest request) {
        return ApiResponse.success(memberService.add(
                currentUser.userId(), projectId, request.userId(), request.role()));
    }

    @PatchMapping("/{userId}")
    public ApiResponse<ProjectMemberResponse> changeRole(
            CurrentUser currentUser,
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateProjectMemberRequest request) {
        return ApiResponse.success(memberService.changeRole(
                currentUser.userId(), projectId, userId, request.role()));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> remove(
            CurrentUser currentUser,
            @PathVariable UUID projectId,
            @PathVariable UUID userId) {
        memberService.remove(currentUser.userId(), projectId, userId);
        return ApiResponse.success();
    }
}
