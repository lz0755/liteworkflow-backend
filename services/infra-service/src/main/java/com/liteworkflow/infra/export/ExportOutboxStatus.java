package com.liteworkflow.infra.export;

enum ExportOutboxStatus {
    PENDING,
    FAILED,
    PUBLISHED,
    DEAD
}
