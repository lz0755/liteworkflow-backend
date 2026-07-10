package com.liteworkflow.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMyIdentityRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String displayName) {

    @Override
    public String toString() {
        return "UpdateMyIdentityRequest[email=[REDACTED], displayName=" + displayName + "]";
    }
}
