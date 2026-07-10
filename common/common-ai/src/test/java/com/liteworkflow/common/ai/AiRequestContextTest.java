package com.liteworkflow.common.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiRequestContextTest {

    @Test
    void projectScopeRequiresWorkspaceScope() {
        assertThatThrownBy(() -> new AiRequestContext(
                        "request-1", UUID.randomUUID(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
