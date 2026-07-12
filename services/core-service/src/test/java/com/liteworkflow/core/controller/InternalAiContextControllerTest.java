package com.liteworkflow.core.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.application.AiContextApplicationService;
import com.liteworkflow.core.config.CoreProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalAiContextControllerTest {

    @Test
    void rejectsInvalidServiceCredentialBeforeReadingContext() {
        AiContextApplicationService service = mock(AiContextApplicationService.class);
        CoreProperties properties = new CoreProperties();
        properties.setInternalToken("internal-test-token");
        InternalAiContextController controller = new InternalAiContextController(service, properties);

        assertThatThrownBy(() -> controller.project(
                "wrong-token", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        verifyNoInteractions(service);
    }
}
