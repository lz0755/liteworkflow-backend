package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.core.application.NotificationContactApplicationService;
import com.liteworkflow.core.config.CoreProperties;
import com.liteworkflow.core.dto.response.NotificationContactResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Internal, narrowly scoped contact lookup for infra-service email delivery. */
@RestController
public class InternalNotificationContactController {

    private final NotificationContactApplicationService service;
    private final CoreProperties properties;

    public InternalNotificationContactController(
            NotificationContactApplicationService service,
            CoreProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/internal/v1/notification-contacts/{userId}")
    public ApiResponse<NotificationContactResponse> get(
            @RequestHeader("X-Internal-Token") String internalToken,
            @PathVariable UUID userId) {
        requireInternalToken(internalToken);
        return ApiResponse.success(service.get(userId));
    }

    private void requireInternalToken(String actual) {
        byte[] expectedBytes = properties.getInternalToken().getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "Invalid internal service credential");
        }
    }
}
