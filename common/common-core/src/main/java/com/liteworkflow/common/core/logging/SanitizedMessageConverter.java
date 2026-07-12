package com.liteworkflow.common.core.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Logback converter that redacts the fully formatted SLF4J message. */
public final class SanitizedMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataSanitizer.sanitizeMessage(event.getFormattedMessage());
    }
}
