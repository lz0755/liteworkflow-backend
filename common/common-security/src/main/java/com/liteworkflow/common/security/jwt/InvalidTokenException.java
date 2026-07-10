package com.liteworkflow.common.security.jwt;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;

public final class InvalidTokenException extends BizException {

    public InvalidTokenException() {
        super(CommonErrorCode.UNAUTHORIZED, "Invalid or expired access token");
    }

    public InvalidTokenException(Throwable cause) {
        super(CommonErrorCode.UNAUTHORIZED, "Invalid or expired access token", cause);
    }
}
