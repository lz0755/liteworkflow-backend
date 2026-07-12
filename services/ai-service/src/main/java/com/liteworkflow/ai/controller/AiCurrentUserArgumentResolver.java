package com.liteworkflow.ai.controller;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.common.security.jwt.InvalidTokenException;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AiCurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtTokenService tokens;

    public AiCurrentUserArgumentResolver(JwtTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public CurrentUser resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw unauthorized();
        }
        try {
            CurrentUser user = tokens.parseAccessToken(JwtTokenService.removeBearerPrefix(
                    request.getHeader(HttpHeaders.AUTHORIZATION)));
            rejectMismatchedGatewayIdentity(request, user);
            return user;
        } catch (InvalidTokenException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private void rejectMismatchedGatewayIdentity(HttpServletRequest request, CurrentUser user) {
        String forwardedUserId = request.getHeader("X-User-Id");
        String forwardedUsername = request.getHeader("X-Username");
        if ((forwardedUserId != null && !forwardedUserId.equals(user.userId().toString()))
                || (forwardedUsername != null && !forwardedUsername.equals(user.username()))) {
            throw unauthorized();
        }
    }

    private BizException unauthorized() {
        return new BizException(CommonErrorCode.UNAUTHORIZED, "Trusted gateway identity is required");
    }
}
