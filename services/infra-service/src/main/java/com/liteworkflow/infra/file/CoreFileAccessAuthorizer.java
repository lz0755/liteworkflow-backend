package com.liteworkflow.infra.file;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.infra.config.FileStorageProperties;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CoreFileAccessAuthorizer implements FileAccessAuthorizer {
    private final RestClient core;
    private final FileStorageProperties properties;
    public CoreFileAccessAuthorizer(RestClient coreRestClient, FileStorageProperties properties) {
        this.core = coreRestClient; this.properties = properties;
    }

    @Override
    public AccessContext authorize(UUID userId, FileScope scope, UUID resourceId, AccessAction action) {
        if (scope == FileScope.USER) {
            if (!userId.equals(resourceId)) throw new BizException(CommonErrorCode.FORBIDDEN);
            return AccessContext.user(userId);
        }
        try {
            ApiResponse<AccessContext> response = core.get()
                    .uri(uri -> uri.path("/internal/v1/file-access/{scope}/{id}")
                            .queryParam("userId", userId).queryParam("action", action).build(scope, resourceId))
                    .header("X-Internal-Token", properties.getInternalToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null || !response.successful() || response.data() == null) {
                throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, "Permission service returned no decision");
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            if (status.value() == 403 || status.value() == 409) throw new BizException(CommonErrorCode.FORBIDDEN);
            if (status.value() == 404) throw new BizException(CommonErrorCode.NOT_FOUND);
            throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, "Permission service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, "Permission service is unavailable", exception);
        }
    }
}
