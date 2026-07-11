package com.liteworkflow.core.domain;

import java.io.Serializable;
import java.util.UUID;

public record IssueSubscriberId(UUID issueId, UUID userId) implements Serializable {
}
