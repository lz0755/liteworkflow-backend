package com.liteworkflow.core.dto.response;

import java.time.Instant;
import java.util.UUID;

public record IssueSubscriptionResponse(UUID issueId, UUID userId, boolean subscribed, Instant subscribedAt) {
}
