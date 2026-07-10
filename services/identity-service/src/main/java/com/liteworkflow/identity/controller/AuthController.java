package com.liteworkflow.identity.controller;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.identity.application.AuthenticationService;
import com.liteworkflow.identity.application.IdentityDirectoryMutationService;
import com.liteworkflow.identity.dto.request.ChangePasswordRequest;
import com.liteworkflow.identity.dto.request.ForgotPasswordRequest;
import com.liteworkflow.identity.dto.request.LoginRequest;
import com.liteworkflow.identity.dto.request.RefreshRequest;
import com.liteworkflow.identity.dto.request.RegisterRequest;
import com.liteworkflow.identity.dto.request.ResetPasswordRequest;
import com.liteworkflow.identity.dto.request.UpdateMyIdentityRequest;
import com.liteworkflow.identity.dto.response.AuthTokensResponse;
import com.liteworkflow.identity.dto.response.IdentityUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final IdentityDirectoryMutationService directoryMutationService;
    private final CurrentIdentityUserResolver currentUserResolver;

    public AuthController(
            AuthenticationService authenticationService,
            IdentityDirectoryMutationService directoryMutationService,
            CurrentIdentityUserResolver currentUserResolver) {
        this.authenticationService = authenticationService;
        this.directoryMutationService = directoryMutationService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthTokensResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(AuthTokensResponse.from(
                authenticationService.register(request.email(), request.displayName(), request.password())));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokensResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(AuthTokensResponse.from(
                authenticationService.login(request.email(), request.password(), servletRequest.getRemoteAddr())));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(AuthTokensResponse.from(authenticationService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        currentUserResolver.resolve(servletRequest);
        authenticationService.logout(request.refreshToken());
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<IdentityUserResponse> me(HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        return ApiResponse.success(IdentityUserResponse.from(authenticationService.me(user.userId())));
    }

    @PatchMapping("/me")
    public ApiResponse<IdentityUserResponse> updateMe(
            @Valid @RequestBody UpdateMyIdentityRequest request, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        return ApiResponse.success(IdentityUserResponse.from(
                directoryMutationService.updateSelf(user.userId(), request.email(), request.displayName())));
    }

    @PostMapping("/change-password")
    public ApiResponse<AuthTokensResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest servletRequest) {
        CurrentUser user = currentUserResolver.resolve(servletRequest);
        return ApiResponse.success(AuthTokensResponse.from(
                authenticationService.changePassword(user.userId(), request.currentPassword(), request.newPassword())));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.forgotPassword(request.email());
        return ApiResponse.success();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.success();
    }
}
