package com.liteworkflow.ai.application;

import com.liteworkflow.ai.domain.AiTokenUsage;

public final class AiCallFailure extends RuntimeException {

    private final AiErrorCode errorCode;
    private final AiTokenUsage usage;
    private final int responseLength;

    public AiCallFailure(AiErrorCode errorCode, AiTokenUsage usage, Throwable cause) {
        this(errorCode, usage, 0, cause);
    }

    public AiCallFailure(AiErrorCode errorCode, AiTokenUsage usage, int responseLength, Throwable cause) {
        super(errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
        this.usage = usage == null ? AiTokenUsage.ZERO : usage;
        this.responseLength = Math.max(0, responseLength);
    }

    public AiErrorCode errorCode() {
        return errorCode;
    }

    public AiTokenUsage usage() {
        return usage;
    }

    public int responseLength() {
        return responseLength;
    }
}
