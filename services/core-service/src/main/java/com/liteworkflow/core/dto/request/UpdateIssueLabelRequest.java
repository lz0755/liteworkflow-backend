package com.liteworkflow.core.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateIssueLabelRequest(
        @Size(max = 80) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) {
}
