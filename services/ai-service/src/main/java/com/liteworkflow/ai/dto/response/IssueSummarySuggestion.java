package com.liteworkflow.ai.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record IssueSummarySuggestion(
        @NotBlank @Size(max = 4000) String summary,
        @NotNull @Size(max = 12) List<@NotBlank @Size(max = 500) String> keyPoints,
        @NotNull @Size(max = 12) List<@NotBlank @Size(max = 500) String> risks,
        @NotNull @Size(max = 12) List<@NotBlank @Size(max = 500) String> nextActions) {
}
