package com.liteworkflow.common.file.storage;

import java.util.regex.Pattern;

public final class ObjectKeys {

    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cntrl}]");

    private ObjectKeys() {
    }

    public static String requireSafe(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (objectKey.length() > 1024) {
            throw new IllegalArgumentException("objectKey must not exceed 1024 characters");
        }
        if (objectKey.startsWith("/") || objectKey.endsWith("/") || objectKey.contains("\\")) {
            throw new IllegalArgumentException("objectKey must be a relative object path");
        }
        if (CONTROL_CHARACTER.matcher(objectKey).find()) {
            throw new IllegalArgumentException("objectKey must not contain control characters");
        }
        for (String segment : objectKey.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("objectKey contains an unsafe path segment");
            }
        }
        return objectKey;
    }
}
