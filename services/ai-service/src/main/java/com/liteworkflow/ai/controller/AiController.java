package com.liteworkflow.ai.controller;

import com.liteworkflow.ai.application.AiApplicationService;
import com.liteworkflow.ai.application.AiStreamingService;
import com.liteworkflow.ai.dto.request.AssistRequest;
import com.liteworkflow.ai.dto.request.AskProjectRequest;
import com.liteworkflow.ai.dto.request.BreakdownIssueRequest;
import com.liteworkflow.ai.dto.request.GenerateIssuesRequest;
import com.liteworkflow.ai.dto.request.SummarizeIssueRequest;
import com.liteworkflow.ai.dto.request.WeeklyReportRequest;
import com.liteworkflow.ai.dto.response.AssistResponse;
import com.liteworkflow.ai.dto.response.BreakdownSuggestion;
import com.liteworkflow.ai.dto.response.ConversationDetailResponse;
import com.liteworkflow.ai.dto.response.ConversationSummaryResponse;
import com.liteworkflow.ai.dto.response.GenerateIssuesSuggestion;
import com.liteworkflow.ai.dto.response.IssueSummarySuggestion;
import com.liteworkflow.ai.dto.response.ProjectAskResponse;
import com.liteworkflow.ai.dto.response.StructuredSuggestionResponse;
import com.liteworkflow.ai.dto.response.WeeklyReportSuggestion;
import com.liteworkflow.ai.dto.stream.AiStreamEventData;
import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.security.user.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class AiController {

    private final AiApplicationService service;
    private final AiStreamingService streamingService;

    public AiController(AiApplicationService service, AiStreamingService streamingService) {
        this.service = service;
        this.streamingService = streamingService;
    }

    @PostMapping("/api/v1/ai/assist")
    public ApiResponse<AssistResponse> assist(
            CurrentUser user,
            @Valid @RequestBody AssistRequest request) {
        return ApiResponse.success(service.assist(user.userId(), request));
    }

    @PostMapping(
            path = "/api/v1/ai/assist/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AiStreamEventData>> streamAssist(
            CurrentUser user,
            @Valid @RequestBody AssistRequest request,
            HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return streamingService.assist(user.userId(), request);
    }

    @PostMapping("/api/v1/ai/issues/generate")
    public ApiResponse<StructuredSuggestionResponse<GenerateIssuesSuggestion>> generateIssues(
            CurrentUser user,
            @Valid @RequestBody GenerateIssuesRequest request) {
        return ApiResponse.success(service.generateIssues(user.userId(), request));
    }

    @PostMapping("/api/v1/ai/issues/{issueId}/breakdown")
    public ApiResponse<StructuredSuggestionResponse<BreakdownSuggestion>> breakdownIssue(
            CurrentUser user,
            @PathVariable UUID issueId,
            @Valid @RequestBody BreakdownIssueRequest request) {
        return ApiResponse.success(service.breakdownIssue(user.userId(), issueId, request));
    }

    @PostMapping("/api/v1/ai/issues/{issueId}/summarize")
    public ApiResponse<StructuredSuggestionResponse<IssueSummarySuggestion>> summarizeIssue(
            CurrentUser user,
            @PathVariable UUID issueId,
            @Valid @RequestBody SummarizeIssueRequest request) {
        return ApiResponse.success(service.summarizeIssue(user.userId(), issueId, request));
    }

    @PostMapping("/api/v1/ai/projects/{projectId}/weekly-report")
    public ApiResponse<StructuredSuggestionResponse<WeeklyReportSuggestion>> weeklyReport(
            CurrentUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody WeeklyReportRequest request) {
        return ApiResponse.success(service.weeklyReport(user.userId(), projectId, request));
    }

    @PostMapping("/api/v1/ai/projects/{projectId}/ask")
    public ApiResponse<ProjectAskResponse> askProject(
            CurrentUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody AskProjectRequest request) {
        return ApiResponse.success(service.askProject(user.userId(), projectId, request));
    }

    @GetMapping("/api/v1/ai/conversations")
    public ApiResponse<PageResult<ConversationSummaryResponse>> listConversations(
            CurrentUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.listConversations(user.userId(), page, size));
    }

    @GetMapping("/api/v1/ai/conversations/{conversationId}")
    public ApiResponse<ConversationDetailResponse> getConversation(
            CurrentUser user,
            @PathVariable UUID conversationId) {
        return ApiResponse.success(service.getConversation(user.userId(), conversationId));
    }
}
