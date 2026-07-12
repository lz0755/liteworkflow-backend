package com.liteworkflow.common.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LogbackConfigurationResourceTest {

    @Test
    void sharedConfigurationBoundsApplicationAndErrorLogs() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/com/liteworkflow/common/logging/logback-common.xml")) {
            assertThat(stream).isNotNull();
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml)
                    .contains("SizeAndTimeBasedRollingPolicy")
                    .contains("${LOG_MAX_HISTORY:-14}")
                    .contains("${LOG_TOTAL_SIZE_CAP:-256MB}")
                    .contains("${LOG_ERROR_TOTAL_SIZE_CAP:-64MB}")
                    .contains("application.log", "error.log")
                    .contains("SanitizedMessageConverter", "SanitizedThrowableProxyConverter")
                    .contains("<level>ERROR</level>");
        }
    }
}
