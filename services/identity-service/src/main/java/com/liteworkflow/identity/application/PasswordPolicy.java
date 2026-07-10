package com.liteworkflow.identity.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_BCRYPT_BYTES = 72;

    public void validate(String password) {
        if (password == null || password.isBlank() || password.length() < MIN_LENGTH) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "password must contain at least 12 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "password must be at most 72 UTF-8 bytes");
        }
    }
}
