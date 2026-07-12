package com.liteworkflow.ai.dto.response;

import java.util.List;

public record ConversationDetailResponse(
        ConversationSummaryResponse conversation,
        List<MessageResponse> messages) {
}
