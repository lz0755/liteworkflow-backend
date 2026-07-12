package com.liteworkflow.common.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataSanitizerTest {

    @Test
    void redactsNamedSecretsWhileKeepingUsefulLabels() {
        String sanitized = SensitiveDataSanitizer.sanitize(
                "password=hunter2 Authorization: Bearer abc.def_123 apiKey=sk-supersecret123");

        assertThat(sanitized)
                .contains("password=[REDACTED]", "Authorization: Bearer [REDACTED]")
                .doesNotContain("hunter2", "abc.def_123", "supersecret123");
    }

    @Test
    void keepsBearerPrefixWhenItIsNotPartOfANamedField() {
        assertThat(SensitiveDataSanitizer.sanitize("upstream rejected Bearer opaque-value-123"))
                .isEqualTo("upstream rejected Bearer [REDACTED]");
    }

    @Test
    void redactsCompleteUserContentAndEmailAddresses() {
        String sanitized = SensitiveDataSanitizer.sanitize(
                "keyword=entire private search phrase\nprompt=include every secret detail\n"
                        + "fileBody=confidential file text\nmailBody=hello alice@example.com");

        assertThat(sanitized)
                .contains("keyword=[REDACTED]", "prompt=[REDACTED]", "fileBody=[REDACTED]", "mailBody=[REDACTED]")
                .doesNotContain(
                        "private search phrase",
                        "every secret detail",
                        "confidential file text",
                        "alice@example.com");
    }

    @Test
    void neutralizesLineBreaksInOrdinaryLogMessages() {
        assertThat(SensitiveDataSanitizer.sanitizeMessage("first\nforged log line"))
                .isEqualTo("first\\nforged log line");
    }
}
