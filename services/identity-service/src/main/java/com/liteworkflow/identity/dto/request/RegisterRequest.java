package com.liteworkflow.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String displayName,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank String password) {

    @Override
    public String toString() {
        return "RegisterRequest[email=[REDACTED], displayName=" + displayName + ", password=[REDACTED]]";
    }
}
