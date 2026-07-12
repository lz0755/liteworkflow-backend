package com.liteworkflow.infra.file;

import com.liteworkflow.common.core.error.ErrorCode;

public enum FileErrorCode implements ErrorCode {
    FILE_NOT_FOUND("INFRA_FILE_404", "File was not found", 404),
    FILE_TOO_LARGE("INFRA_FILE_SIZE_413", "File exceeds the allowed size", 413),
    FILE_NAME_INVALID("INFRA_FILE_NAME_400", "File name is invalid", 400),
    FILE_EXTENSION_NOT_ALLOWED("INFRA_FILE_EXTENSION_400", "File extension is not allowed", 400),
    FILE_CONTENT_MISMATCH("INFRA_FILE_CONTENT_400", "File content does not match its extension and MIME type", 400),
    FILE_UPLOAD_FAILED("INFRA_FILE_UPLOAD_503", "File upload could not be completed", 503);

    private final String code; private final String message; private final int status;
    FileErrorCode(String code, String message, int status) { this.code = code; this.message = message; this.status = status; }
    public String code() { return code; }
    public String defaultMessage() { return message; }
    public int httpStatus() { return status; }
}
