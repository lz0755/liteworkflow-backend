package com.liteworkflow.ai.application;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.error.BizException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CoreAiContextClient {

    private final RestClient restClient;

    public CoreAiContextClient(@Qualifier("coreAiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public WorkspaceContext workspace(UUID userId, UUID workspaceId) {
        WorkspaceContext context = get(
                "/internal/v1/ai-context/workspaces/{workspaceId}?userId={userId}",
                new ParameterizedTypeReference<ApiResponse<WorkspaceContext>>() {},
                workspaceId, userId);
        if (!workspaceId.equals(context.workspaceId())) {
            throw new BizException(AiErrorCode.RESOURCE_NOT_FOUND);
        }
        return context;
    }

    public ProjectContext project(UUID userId, UUID workspaceId, UUID projectId) {
        ProjectContext context = get(
                "/internal/v1/ai-context/projects/{projectId}?userId={userId}",
                new ParameterizedTypeReference<ApiResponse<ProjectContext>>() {},
                projectId, userId);
        requireScope(workspaceId, projectId, context.workspaceId(), context.projectId());
        return context;
    }

    public IssueContext issue(
            UUID userId, UUID workspaceId, UUID expectedProjectId, UUID issueId) {
        IssueContext context = get(
                "/internal/v1/ai-context/issues/{issueId}?userId={userId}&expectedProjectId={projectId}",
                new ParameterizedTypeReference<ApiResponse<IssueContext>>() {},
                issueId, userId, expectedProjectId);
        requireScope(workspaceId, expectedProjectId, context.workspaceId(), context.projectId());
        if (!issueId.equals(context.issueId())) {
            throw new BizException(AiErrorCode.RESOURCE_NOT_FOUND);
        }
        return context;
    }

    public WeeklyContext weekly(
            UUID userId,
            UUID workspaceId,
            UUID projectId,
            LocalDate from,
            LocalDate to) {
        WeeklyContext context = get(
                "/internal/v1/ai-context/projects/{projectId}/weekly-report"
                        + "?userId={userId}&from={from}&to={to}",
                new ParameterizedTypeReference<ApiResponse<WeeklyContext>>() {},
                projectId, userId, from, to);
        requireScope(workspaceId, projectId, context.workspaceId(), context.projectId());
        return context;
    }

    private <T> T get(String uri, ParameterizedTypeReference<ApiResponse<T>> type, Object... variables) {
        try {
            ApiResponse<T> response = restClient.get().uri(uri, variables).retrieve().body(type);
            if (response == null || !response.successful() || response.data() == null) {
                throw new BizException(AiErrorCode.CORE_UNAVAILABLE);
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            if (status.value() == 404) {
                throw new BizException(AiErrorCode.RESOURCE_NOT_FOUND);
            }
            if (status.value() == 403) {
                throw new BizException(AiErrorCode.RESOURCE_FORBIDDEN);
            }
            throw new BizException(AiErrorCode.CORE_UNAVAILABLE);
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BizException(AiErrorCode.CORE_UNAVAILABLE);
        }
    }

    private static void requireScope(
            UUID expectedWorkspaceId,
            UUID expectedProjectId,
            UUID actualWorkspaceId,
            UUID actualProjectId) {
        if ((expectedWorkspaceId != null && !expectedWorkspaceId.equals(actualWorkspaceId))
                || !expectedProjectId.equals(actualProjectId)) {
            throw new BizException(AiErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    public record WorkspaceContext(UUID workspaceId) {}

    public record ProjectContext(
            UUID workspaceId, UUID projectId, String name, String description) {}

    public record IssueContext(
            UUID workspaceId,
            UUID projectId,
            UUID issueId,
            String title,
            String description,
            String activityDigest) {}

    public record WeeklyContext(
            UUID workspaceId,
            UUID projectId,
            String projectName,
            String projectDescription,
            String activityDigest) {}
}
