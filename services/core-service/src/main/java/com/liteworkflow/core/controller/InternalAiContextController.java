package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.application.AiContextApplicationService;
import com.liteworkflow.core.config.CoreProperties;
import com.liteworkflow.core.dto.response.AiIssueContextResponse;
import com.liteworkflow.core.dto.response.AiProjectContextResponse;
import com.liteworkflow.core.dto.response.AiWeeklyReportContextResponse;
import com.liteworkflow.core.dto.response.AiWorkspaceContextResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal, service-authenticated resource context for suggestion-only AI operations. */
@RestController
public class InternalAiContextController {

    private final AiContextApplicationService service;
    private final CoreProperties properties;

    public InternalAiContextController(AiContextApplicationService service, CoreProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/internal/v1/ai-context/workspaces/{workspaceId}")
    public ApiResponse<AiWorkspaceContextResponse> workspace(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable UUID workspaceId,
            @RequestParam UUID userId) {
        requireInternalToken(token);
        return ApiResponse.success(service.workspace(workspaceId, userId));
    }

    @GetMapping("/internal/v1/ai-context/projects/{projectId}")
    public ApiResponse<AiProjectContextResponse> project(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable UUID projectId,
            @RequestParam UUID userId) {
        requireInternalToken(token);
        return ApiResponse.success(service.project(projectId, userId));
    }

    @GetMapping("/internal/v1/ai-context/issues/{issueId}")
    public ApiResponse<AiIssueContextResponse> issue(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable UUID issueId,
            @RequestParam UUID userId,
            @RequestParam(required = false) UUID expectedProjectId) {
        requireInternalToken(token);
        return ApiResponse.success(service.issue(issueId, expectedProjectId, userId));
    }

    @GetMapping("/internal/v1/ai-context/projects/{projectId}/weekly-report")
    public ApiResponse<AiWeeklyReportContextResponse> weekly(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable UUID projectId,
            @RequestParam UUID userId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        requireInternalToken(token);
        return ApiResponse.success(service.weekly(projectId, userId, from, to));
    }

    private void requireInternalToken(String actual) {
        byte[] expected = properties.getInternalToken().getBytes(StandardCharsets.UTF_8);
        byte[] supplied = actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "Invalid internal service credential");
        }
    }
}
