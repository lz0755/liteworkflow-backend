package com.liteworkflow.common.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liteworkflow.common.core.error.BizException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UserContextTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void nestedScopeRestoresPreviousUser() {
        CurrentUser outer = user("outer");
        CurrentUser inner = user("inner");

        try (UserContext.Scope ignored = UserContext.withUser(outer)) {
            try (UserContext.Scope nested = UserContext.withUser(inner)) {
                assertThat(UserContext.requireCurrent()).isEqualTo(inner);
            }
            assertThat(UserContext.requireCurrent()).isEqualTo(outer);
        }

        assertThat(UserContext.current()).isEmpty();
    }

    @Test
    void requireCurrentRejectsAnonymousAccess() {
        assertThatThrownBy(UserContext::requireCurrent).isInstanceOf(BizException.class);
    }

    private CurrentUser user(String username) {
        return new CurrentUser(UUID.randomUUID(), username, Set.of("MEMBER"));
    }
}
