package com.liteworkflow.common.file.storage;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;

public final class ObjectStorageException extends BizException {

    public ObjectStorageException(String message, Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }
}
