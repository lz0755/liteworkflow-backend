package com.liteworkflow.core.dto.response;

import com.liteworkflow.core.domain.IssueState;
import com.liteworkflow.core.domain.IssueStateCategory;
import java.util.UUID;

public record IssueStateResponse(
        UUID id,
        String name,
        IssueStateCategory category,
        int position,
        boolean defaultState) {

    public static IssueStateResponse from(IssueState state) {
        return new IssueStateResponse(
                state.getId(), state.getName(), state.getCategory(), state.getPosition(), state.isDefaultState());
    }
}
