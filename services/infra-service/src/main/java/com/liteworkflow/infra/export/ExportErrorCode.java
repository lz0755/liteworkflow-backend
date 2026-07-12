package com.liteworkflow.infra.export;

import com.liteworkflow.common.core.error.ErrorCode;

public enum ExportErrorCode implements ErrorCode {
    EXPORT_NOT_FOUND("INFRA_EXPORT_404", "Export job was not found", 404),
    EXPORT_NOT_READY("INFRA_EXPORT_NOT_READY_409", "Export file is not ready", 409),
    EXPORT_REQUEST_FAILED("INFRA_EXPORT_REQUEST_503", "Export request could not be queued", 503),
    EXPORT_FILE_INVALID("INFRA_EXPORT_FILE_500", "Export file metadata is invalid", 500);

    private final String code;
    private final String message;
    private final int status;

    ExportErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return message; }
    @Override public int httpStatus() { return status; }
}
