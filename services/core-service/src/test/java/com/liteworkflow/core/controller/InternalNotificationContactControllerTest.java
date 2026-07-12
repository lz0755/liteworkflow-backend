package com.liteworkflow.core.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.application.NotificationContactApplicationService;
import com.liteworkflow.core.config.CoreProperties;
import com.liteworkflow.core.dto.response.NotificationContactResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalNotificationContactControllerTest {

    @Test
    void requiresTheInternalCredentialAndRedactsContactToString() {
        UUID userId = UUID.randomUUID();
        NotificationContactApplicationService service = mock(NotificationContactApplicationService.class);
        CoreProperties properties = new CoreProperties();
        properties.setInternalToken("internal-test-token");
        InternalNotificationContactController controller =
                new InternalNotificationContactController(service, properties);
        NotificationContactResponse contact =
                new NotificationContactResponse(userId, "private@example.test");
        when(service.get(userId)).thenReturn(contact);

        assertThat(controller.get("internal-test-token", userId).data()).isEqualTo(contact);
        verify(service).get(userId);
        assertThat(contact.toString())
                .contains("email=[REDACTED]")
                .doesNotContain("private@example.test");

        assertThatThrownBy(() -> controller.get("wrong-token", userId))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }
}
