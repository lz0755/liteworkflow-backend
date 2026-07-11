package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record IssueLabelRelationId(
        @Column(name = "issue_id") UUID issueId,
        @Column(name = "label_id") UUID labelId) implements Serializable {
}
