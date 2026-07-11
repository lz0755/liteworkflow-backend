package com.liteworkflow.core.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.core.application.UserProfileApplicationService;
import com.liteworkflow.core.application.UserSearchApplicationService;
import com.liteworkflow.core.dto.request.UpdateUserProfileRequest;
import com.liteworkflow.core.dto.response.UserProfileResponse;
import com.liteworkflow.core.dto.response.UserSearchResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileApplicationService profileService;
    private final UserSearchApplicationService searchService;

    public UserController(
            UserProfileApplicationService profileService,
            UserSearchApplicationService searchService) {
        this.profileService = profileService;
        this.searchService = searchService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(CurrentUser currentUser) {
        return ApiResponse.success(profileService.get(currentUser.userId()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMe(
            CurrentUser currentUser, @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.success(profileService.update(currentUser.userId(), request));
    }

    @GetMapping("/search")
    public ApiResponse<PageResult<UserSearchResponse>> search(
            CurrentUser currentUser,
            @RequestParam String keyword,
            @RequestParam String contextType,
            @RequestParam UUID contextId,
            @RequestParam(defaultValue = "true") boolean excludeExistingMembers,
            @RequestParam(required = false) Boolean excludeExisting,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        boolean exclude = excludeExisting == null ? excludeExistingMembers : excludeExisting;
        return ApiResponse.success(searchService.search(
                currentUser.userId(), keyword, contextType, contextId, exclude, page, size));
    }
}
