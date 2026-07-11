package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.application.IssueCatalogApplicationService;
import com.liteworkflow.core.dto.request.CreateIssueLabelRequest;
import com.liteworkflow.core.dto.request.CreateIssueStateRequest;
import com.liteworkflow.core.dto.request.UpdateIssueLabelRequest;
import com.liteworkflow.core.dto.request.UpdateIssueStateRequest;
import com.liteworkflow.core.dto.response.IssueLabelResponse;
import com.liteworkflow.core.dto.response.IssueStateResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IssueCatalogController {

    private final IssueCatalogApplicationService catalogService;

    public IssueCatalogController(IssueCatalogApplicationService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/api/v1/projects/{projectId}/issue-states")
    public ApiResponse<List<IssueStateResponse>> listStates(CurrentUser user, @PathVariable UUID projectId) {
        return ApiResponse.success(catalogService.listStates(user.userId(), projectId));
    }

    @PostMapping("/api/v1/projects/{projectId}/issue-states")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueStateResponse> createState(
            CurrentUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateIssueStateRequest request) {
        return ApiResponse.success(catalogService.createState(user.userId(), projectId, request));
    }

    @PatchMapping("/api/v1/projects/{projectId}/issue-states/{stateId}")
    public ApiResponse<IssueStateResponse> updateState(
            CurrentUser user,
            @PathVariable UUID projectId,
            @PathVariable UUID stateId,
            @Valid @RequestBody UpdateIssueStateRequest request) {
        return ApiResponse.success(catalogService.updateState(user.userId(), projectId, stateId, request));
    }

    @DeleteMapping("/api/v1/projects/{projectId}/issue-states/{stateId}")
    public ApiResponse<Void> deleteState(
            CurrentUser user, @PathVariable UUID projectId, @PathVariable UUID stateId) {
        catalogService.deleteState(user.userId(), projectId, stateId);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/projects/{projectId}/labels")
    public ApiResponse<List<IssueLabelResponse>> listLabels(CurrentUser user, @PathVariable UUID projectId) {
        return ApiResponse.success(catalogService.listLabels(user.userId(), projectId));
    }

    @PostMapping("/api/v1/projects/{projectId}/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueLabelResponse> createLabel(
            CurrentUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateIssueLabelRequest request) {
        return ApiResponse.success(catalogService.createLabel(user.userId(), projectId, request));
    }

    @PatchMapping("/api/v1/projects/{projectId}/labels/{labelId}")
    public ApiResponse<IssueLabelResponse> updateLabel(
            CurrentUser user,
            @PathVariable UUID projectId,
            @PathVariable UUID labelId,
            @Valid @RequestBody UpdateIssueLabelRequest request) {
        return ApiResponse.success(catalogService.updateLabel(user.userId(), projectId, labelId, request));
    }

    @DeleteMapping("/api/v1/projects/{projectId}/labels/{labelId}")
    public ApiResponse<Void> deleteLabel(
            CurrentUser user, @PathVariable UUID projectId, @PathVariable UUID labelId) {
        catalogService.deleteLabel(user.userId(), projectId, labelId);
        return ApiResponse.success();
    }
}
