package com.liteworkflow.common.core.logging;

import java.util.regex.Pattern;

/** Defense-in-depth redaction for messages and exception text emitted by Logback. */
public final class SensitiveDataSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final Pattern SENSITIVE_CONTENT = Pattern.compile(
            "(?im)(\\b(?:prompt|model(?:Request|Response)|search(?:Term|Keyword|Query)|keyword|"
                    + "file(?:Body|Content|Text)|mail(?:Body|Content|Text)|email(?:Body|Content|Text))"
                    + "\\b[\\\"']?\\s*[:=]\\s*).*$");
    private static final Pattern AUTHORIZATION_WITH_SCHEME = Pattern.compile(
            "(?i)(\\b(?:authorization|proxy-authorization)\\b[\\\"']?\\s*[:=]\\s*)"
                    + "((?:Bearer|Basic)\\s+)[^\\s,;&}\\]]+");
    private static final Pattern AUTHORIZATION_WITHOUT_SCHEME = Pattern.compile(
            "(?i)(\\b(?:authorization|proxy-authorization)\\b[\\\"']?\\s*[:=]\\s*)"
                    + "(?!(?:Bearer|Basic)\\s+)(?:\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|"
                    + "'(?:\\\\.|[^'\\\\])*'|[^\\s,;&}\\]]+)");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(\\b(?:password(?:_hash)?|currentPassword|newPassword|passwd|pwd|"
                    + "access[ _-]?token|refresh[ _-]?token|reset[ _-]?token|id[ _-]?token|token|"
                    + "cookie|set-cookie|api[ _-]?key|"
                    + "secret[ _-]?key|client[ _-]?secret|smtp[ _-]?password)"
                    + "\\b[\\\"']?\\s*[:=]\\s*)"
                    + "(?:(?:Bearer|Basic)\\s+)?(?:\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|"
                    + "'(?:\\\\.|[^'\\\\])*'|[^\\s,;&}\\]]+)");
    private static final Pattern BEARER_OR_BASIC = Pattern.compile(
            "(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern PREFIXED_API_KEY = Pattern.compile(
            "(?i)\\b(?:sk|rk|pk|api)[-_][A-Za-z0-9_-]{8,}\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9._%+-])");

    private SensitiveDataSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String sanitized = replace(SENSITIVE_CONTENT, value, "$1" + REDACTED);
        sanitized = replace(AUTHORIZATION_WITH_SCHEME, sanitized, "$1$2" + REDACTED);
        sanitized = replace(AUTHORIZATION_WITHOUT_SCHEME, sanitized, "$1" + REDACTED);
        sanitized = replace(NAMED_SECRET, sanitized, "$1" + REDACTED);
        sanitized = replace(BEARER_OR_BASIC, sanitized, "$1 " + REDACTED);
        sanitized = replace(JWT, sanitized, REDACTED);
        sanitized = replace(PREFIXED_API_KEY, sanitized, REDACTED);
        return replace(EMAIL, sanitized, "[REDACTED_EMAIL]");
    }

    /** A log message must stay on one physical line to prevent log-forging input. */
    public static String sanitizeMessage(String value) {
        String sanitized = sanitize(value);
        return sanitized == null ? null : sanitized.replace("\r", "\\r").replace("\n", "\\n");
    }

    public static String sanitizeSingleLine(String value) {
        return sanitizeMessage(value);
    }

    private static String replace(Pattern pattern, String value, String replacement) {
        return pattern.matcher(value).replaceAll(replacement);
    }
}
