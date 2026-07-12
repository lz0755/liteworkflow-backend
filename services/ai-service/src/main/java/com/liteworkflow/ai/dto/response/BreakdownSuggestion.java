package com.liteworkflow.ai.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BreakdownSuggestion(
        @NotEmpty @Size(max = 20) List<@Valid SubtaskSuggestion> subtasks) {
}
