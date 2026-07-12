package com.liteworkflow.ai.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubtaskSuggestion(
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 500) String completionSignal) {
}
