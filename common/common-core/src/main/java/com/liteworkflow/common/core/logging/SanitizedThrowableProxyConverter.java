package com.liteworkflow.common.core.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Applies the same redaction rules to exception messages and stack traces. */
public final class SanitizedThrowableProxyConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataSanitizer.sanitize(super.convert(event));
    }
}
