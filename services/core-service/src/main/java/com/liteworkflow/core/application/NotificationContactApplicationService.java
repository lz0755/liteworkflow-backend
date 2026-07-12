package com.liteworkflow.core.application;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.core.domain.AccountStatus;
import com.liteworkflow.core.domain.UserDirectory;
import com.liteworkflow.core.dto.response.NotificationContactResponse;
import com.liteworkflow.core.repository.UserDirectoryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationContactApplicationService {

    private final UserDirectoryRepository repository;

    public NotificationContactApplicationService(UserDirectoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NotificationContactResponse get(UUID userId) {
        UserDirectory user = repository.findById(userId)
                .orElseThrow(() -> new BizException(CoreErrorCode.USER_NOT_FOUND));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BizException(CoreErrorCode.USER_NOT_ACTIVE);
        }
        return new NotificationContactResponse(user.getUserId(), user.getEmailDisplay());
    }
}
