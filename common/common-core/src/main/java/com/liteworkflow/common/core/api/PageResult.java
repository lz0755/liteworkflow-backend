package com.liteworkflow.common.core.api;

import java.util.List;
import java.util.Objects;

public record PageResult<T>(List<T> records, long total, long page, long size, long pages) {

    public PageResult {
        Objects.requireNonNull(records, "records must not be null");
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        if (pages < 0) {
            throw new IllegalArgumentException("pages must not be negative");
        }
        records = List.copyOf(records);
    }

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        long pages = total / size + (total % size == 0 ? 0 : 1);
        return new PageResult<>(records, total, page, size, pages);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return of(List.of(), 0, page, size);
    }
}
