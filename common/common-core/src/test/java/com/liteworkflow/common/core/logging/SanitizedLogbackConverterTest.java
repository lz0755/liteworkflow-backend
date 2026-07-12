package com.liteworkflow.common.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

class SanitizedLogbackConverterTest {

    @Test
    void sanitizesFormattedArgumentsAndThrowableText() {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger("sanitizer-test");
        var failure = new IllegalStateException(
                "refreshToken=refresh-secret prompt=do not expose this prompt alice@example.com");
        var event = new LoggingEvent(
                getClass().getName(),
                logger,
                Level.ERROR,
                "Authorization: Bearer {}",
                failure,
                new Object[] {"access-secret"});

        String message = new SanitizedMessageConverter().convert(event);
        var throwableConverter = new SanitizedThrowableProxyConverter();
        throwableConverter.setContext(context);
        throwableConverter.start();
        String throwable = throwableConverter.convert(event);

        assertThat(message)
                .contains("Authorization: Bearer [REDACTED]")
                .doesNotContain("access-secret");
        assertThat(throwable)
                .contains("refreshToken=[REDACTED]", "prompt=[REDACTED]")
                .doesNotContain("refresh-secret", "do not expose this prompt", "alice@example.com");
    }
}
