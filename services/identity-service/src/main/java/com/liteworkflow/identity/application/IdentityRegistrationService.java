package com.liteworkflow.identity.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.identity.domain.IdentityUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Keeps an expected database uniqueness race out of the HTTP 500 path. */
@Service
class IdentityRegistrationService {

    private final IdentityRegistrationTransaction transaction;

    IdentityRegistrationService(IdentityRegistrationTransaction transaction) {
        this.transaction = transaction;
    }

    IdentityUser register(String email, String displayName, String password) {
        try {
            return transaction.register(email, displayName, password);
        } catch (DataIntegrityViolationException exception) {
            throw new BizException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }
}
