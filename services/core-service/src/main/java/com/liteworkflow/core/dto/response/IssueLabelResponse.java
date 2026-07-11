package com.liteworkflow.core.dto.response;

import com.liteworkflow.core.domain.IssueLabel;
import java.util.UUID;

public record IssueLabelResponse(UUID id, String name, String color) {

    public static IssueLabelResponse from(IssueLabel label) {
        return new IssueLabelResponse(label.getId(), label.getName(), label.getColor());
    }
}
