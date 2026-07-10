package com.liteworkflow.identity.domain;

public enum OutboxStatus {
    PENDING,
    FAILED,
    PUBLISHED,
    DEAD
}
