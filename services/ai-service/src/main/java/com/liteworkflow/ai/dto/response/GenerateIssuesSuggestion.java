package com.liteworkflow.ai.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GenerateIssuesSuggestion(
        @NotEmpty @Size(max = 10) List<@Valid IssueSuggestion> suggestions) {
}
