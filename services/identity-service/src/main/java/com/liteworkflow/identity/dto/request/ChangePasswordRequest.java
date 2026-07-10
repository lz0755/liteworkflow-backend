package com.liteworkflow.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank String currentPassword,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=[REDACTED], newPassword=[REDACTED]]";
    }
}
