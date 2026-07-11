package com.liteworkflow.core.domain;

public enum OutboxStatus {
    PENDING,
    FAILED,
    PUBLISHED,
    DEAD
}
