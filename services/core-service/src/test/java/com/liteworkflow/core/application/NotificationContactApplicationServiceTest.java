package com.liteworkflow.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationContactApplicationServiceTest {

    @Test
    void onlyReturnsActiveDirectoryContacts() {
        UUID activeId = UUID.randomUUID();
        UUID disabledId = UUID.randomUUID();
        UserDirectoryRepository repository = mock(UserDirectoryRepository.class);
        when(repository.findById(activeId)).thenReturn(Optional.of(user(activeId, AccountStatus.ACTIVE)));
        when(repository.findById(disabledId)).thenReturn(Optional.of(user(disabledId, AccountStatus.DISABLED)));
        NotificationContactApplicationService service = new NotificationContactApplicationService(repository);

        assertThat(service.get(activeId).email()).isEqualTo("private@example.test");
        assertThatThrownBy(() -> service.get(disabledId))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(CoreErrorCode.USER_NOT_ACTIVE);
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(CoreErrorCode.USER_NOT_FOUND);
    }

    private UserDirectory user(UUID userId, AccountStatus status) {
        return new UserDirectory(
                userId,
                "private@example.test",
                "private@example.test",
                "Private",
                status,
                1,
                Instant.now());
    }
}
