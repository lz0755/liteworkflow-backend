package com.liteworkflow.infra.email;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.core.trace.TraceIds;
import com.liteworkflow.infra.notification.NotificationProperties;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NotificationContactClient {

    private final RestClient core;
    private final NotificationProperties properties;

    public NotificationContactClient(
            RestClient.Builder builder,
            NotificationProperties properties) {
        this.core = builder.baseUrl(properties.getCoreServiceUrl()).build();
        this.properties = properties;
    }

    public NotificationContact get(UUID userId) {
        try {
            ApiResponse<NotificationContact> response = core.get()
                    .uri("/internal/v1/notification-contacts/{userId}", userId)
                    .header("X-Internal-Token", properties.getInternalToken())
                    .headers(headers -> {
                        String traceId = TraceIds.current();
                        if (traceId != null) {
                            headers.set(TraceConstants.TRACE_ID_HEADER, traceId);
                        }
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw new ContactLookupException();
                    })
                    .body(new ParameterizedTypeReference<>() { });
            if (response == null || !response.successful() || response.data() == null) {
                throw new ContactLookupException();
            }
            return response.data();
        } catch (RestClientException exception) {
            throw new ContactLookupException();
        }
    }

    private static final class ContactLookupException extends RuntimeException {
        private ContactLookupException() {
            super("Notification contact lookup failed");
        }
    }
}
