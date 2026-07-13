package com.liteworkflow.ai.rag;

import com.liteworkflow.ai.application.AiErrorCode;
import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.error.BizException;
import java.util.UUID;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CoreRagSourceClient {

    private final RestClient restClient;

    public CoreRagSourceClient(@Qualifier("coreAiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Source issue(UUID issueId) {
        return get("/internal/v1/rag-sources/issues/{sourceId}", issueId);
    }

    public Source comment(UUID commentId) {
        return get("/internal/v1/rag-sources/comments/{sourceId}", commentId);
    }

    public List<Source> deletedIssueComments(UUID issueId) {
        try {
            ApiResponse<List<Source>> response = restClient.get()
                    .uri("/internal/v1/rag-sources/issues/{sourceId}/deleted-comments", issueId)
                    .retrieve().body(new ParameterizedTypeReference<ApiResponse<List<Source>>>() {});
            if (response == null || !response.successful() || response.data() == null) {
                throw new BizException(AiErrorCode.CORE_UNAVAILABLE);
            }
            return List.copyOf(response.data());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new BizException(AiErrorCode.RESOURCE_NOT_FOUND);
            }
            throw new BizException(AiErrorCode.CORE_UNAVAILABLE);
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BizException(AiErrorCode.CORE_UNAVAILABLE);
        }
    }

    private Source get(String uri, UUID sourceId) {
        try {
            ApiResponse<Source> response = restClient.get().uri(uri, sourceId).retrieve().body(
                    new ParameterizedTypeReference<ApiResponse<Source>>() {});
            if (response == null || !response.successful() || response.data() == null) {
                throw new BizException(AiErrorCode.CORE_UNAVAILABLE);
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new BizException(AiErrorCode.RESOURCE_NOT_FOUND);
            }
            throw new BizException(AiErrorCode.CORE_UNAVAILABLE);
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BizException(AiErrorCode.CORE_UNAVAILABLE);
        }
    }

    public record Source(
            UUID workspaceId,
            UUID projectId,
            UUID sourceId,
            long sourceVersion,
            boolean deleted,
            String title,
            String text) {
    }
}
