package com.liteworkflow.identity.controller;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.common.security.jwt.InvalidTokenException;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
class CurrentIdentityUserResolver {

    private final JwtTokenService jwtTokenService;

    CurrentIdentityUserResolver(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    CurrentUser resolve(HttpServletRequest request) {
        try {
            return jwtTokenService.parseAccessToken(
                    JwtTokenService.removeBearerPrefix(request.getHeader(HttpHeaders.AUTHORIZATION)));
        } catch (InvalidTokenException exception) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "Invalid or expired access token");
        }
    }
}
