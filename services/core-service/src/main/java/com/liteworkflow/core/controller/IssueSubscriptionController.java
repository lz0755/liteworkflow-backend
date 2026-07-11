package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.application.IssueSubscriptionApplicationService;
import com.liteworkflow.core.dto.response.IssueSubscriptionResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IssueSubscriptionController {

    private final IssueSubscriptionApplicationService subscriptionService;

    public IssueSubscriptionController(IssueSubscriptionApplicationService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/api/v1/issues/{issueId}/subscription")
    public ApiResponse<IssueSubscriptionResponse> get(CurrentUser currentUser, @PathVariable UUID issueId) {
        return ApiResponse.success(subscriptionService.get(currentUser.userId(), issueId));
    }

    @PutMapping("/api/v1/issues/{issueId}/subscription")
    public ApiResponse<IssueSubscriptionResponse> subscribe(CurrentUser currentUser, @PathVariable UUID issueId) {
        return ApiResponse.success(subscriptionService.subscribe(currentUser.userId(), issueId));
    }

    @DeleteMapping("/api/v1/issues/{issueId}/subscription")
    public ApiResponse<IssueSubscriptionResponse> unsubscribe(CurrentUser currentUser, @PathVariable UUID issueId) {
        return ApiResponse.success(subscriptionService.unsubscribe(currentUser.userId(), issueId));
    }
}
