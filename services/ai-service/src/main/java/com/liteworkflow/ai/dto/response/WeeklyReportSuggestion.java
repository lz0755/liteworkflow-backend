package com.liteworkflow.ai.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WeeklyReportSuggestion(
        @NotBlank @Size(max = 4000) String executiveSummary,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> achievements,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> inProgress,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> risks,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> nextWeek) {
}
