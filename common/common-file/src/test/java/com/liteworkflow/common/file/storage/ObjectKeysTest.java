package com.liteworkflow.common.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ObjectKeysTest {

    @Test
    void allowsTenantScopedKeys() {
        assertThat(ObjectKeys.requireSafe("workspaces/123/files/report.pdf"))
                .isEqualTo("workspaces/123/files/report.pdf");
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() {
        assertThatThrownBy(() -> ObjectKeys.requireSafe("workspace/../secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObjectKeys.requireSafe("/root/file"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
