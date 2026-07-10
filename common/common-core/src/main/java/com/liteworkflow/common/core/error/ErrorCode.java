package com.liteworkflow.common.core.error;

/** Stable error contract shared by HTTP and non-HTTP adapters. */
public interface ErrorCode {

    String code();

    String defaultMessage();

    int httpStatus();
}
