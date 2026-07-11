package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.application.IssueApplicationService;
import com.liteworkflow.core.dto.request.ChangeIssueStateRequest;
import com.liteworkflow.core.dto.request.CreateIssueRequest;
import com.liteworkflow.core.dto.request.ReplaceIssueAssigneesRequest;
import com.liteworkflow.core.dto.request.ReplaceIssueLabelsRequest;
import com.liteworkflow.core.dto.request.UpdateIssueRequest;
import com.liteworkflow.core.dto.response.IssueResponse;
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
public class IssueController {

    private final IssueApplicationService issueService;

    public IssueController(IssueApplicationService issueService) {
        this.issueService = issueService;
    }

    @GetMapping("/api/v1/projects/{projectId}/issues")
    public ApiResponse<PageResult<IssueResponse>> list(
            CurrentUser currentUser,
            @PathVariable UUID projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID stateId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID labelId,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(issueService.list(
                currentUser.userId(), projectId, keyword, stateId, assigneeId, labelId, createdBy, page, size));
    }

    @PostMapping("/api/v1/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueResponse> create(
            CurrentUser currentUser,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateIssueRequest request) {
        return ApiResponse.success(issueService.create(currentUser.userId(), projectId, request));
    }

    @GetMapping("/api/v1/issues/{issueId}")
    public ApiResponse<IssueResponse> get(CurrentUser currentUser, @PathVariable UUID issueId) {
        return ApiResponse.success(issueService.get(currentUser.userId(), issueId));
    }

    @PatchMapping("/api/v1/issues/{issueId}")
    public ApiResponse<IssueResponse> update(
            CurrentUser currentUser,
            @PathVariable UUID issueId,
            @Valid @RequestBody UpdateIssueRequest request) {
        return ApiResponse.success(issueService.update(currentUser.userId(), issueId, request));
    }

    @DeleteMapping("/api/v1/issues/{issueId}")
    public ApiResponse<Void> delete(CurrentUser currentUser, @PathVariable UUID issueId) {
        issueService.delete(currentUser.userId(), issueId);
        return ApiResponse.success();
    }

    @PatchMapping("/api/v1/issues/{issueId}/state")
    public ApiResponse<IssueResponse> changeState(
            CurrentUser currentUser,
            @PathVariable UUID issueId,
            @Valid @RequestBody ChangeIssueStateRequest request) {
        return ApiResponse.success(issueService.changeState(currentUser.userId(), issueId, request));
    }

    @PatchMapping("/api/v1/issues/{issueId}/assignees")
    public ApiResponse<IssueResponse> replaceAssignees(
            CurrentUser currentUser,
            @PathVariable UUID issueId,
            @Valid @RequestBody ReplaceIssueAssigneesRequest request) {
        return ApiResponse.success(issueService.replaceAssignees(
                currentUser.userId(), issueId, request.assigneeIds()));
    }

    @PatchMapping("/api/v1/issues/{issueId}/labels")
    public ApiResponse<IssueResponse> replaceLabels(
            CurrentUser currentUser,
            @PathVariable UUID issueId,
            @Valid @RequestBody ReplaceIssueLabelsRequest request) {
        return ApiResponse.success(issueService.replaceLabels(currentUser.userId(), issueId, request.labelIds()));
    }
}
