package com.liteworkflow.ai.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record IssueSuggestion(
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotEmpty @Size(max = 12) List<@NotBlank @Size(max = 500) String> acceptanceCriteria,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority) {
}
