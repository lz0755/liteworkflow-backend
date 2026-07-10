package com.liteworkflow.identity.infrastructure;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class EmailNormalizer {

    public String normalize(String email) {
        if (email == null) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "email is required");
        }
        String normalized = Normalizer.normalize(email.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 320 || !normalized.contains("@")) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "email is invalid");
        }
        return normalized;
    }
}
