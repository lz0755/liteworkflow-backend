package com.liteworkflow.core.controller;

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
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtTokenService jwtTokenService;

    public CurrentUserArgumentResolver(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
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
            CurrentUser tokenUser = jwtTokenService.parseAccessToken(JwtTokenService.removeBearerPrefix(
                    request.getHeader(HttpHeaders.AUTHORIZATION)));
            rejectMismatchedGatewayIdentity(request, tokenUser);
            return tokenUser;
        } catch (InvalidTokenException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private void rejectMismatchedGatewayIdentity(HttpServletRequest request, CurrentUser tokenUser) {
        String forwardedUserId = request.getHeader(CoreGatewayHeaders.USER_ID);
        String forwardedUsername = request.getHeader(CoreGatewayHeaders.USERNAME);
        if ((forwardedUserId != null && !forwardedUserId.equals(tokenUser.userId().toString()))
                || (forwardedUsername != null && !forwardedUsername.equals(tokenUser.username()))) {
            throw unauthorized();
        }
    }

    private BizException unauthorized() {
        return new BizException(CommonErrorCode.UNAUTHORIZED, "Trusted gateway identity is required");
    }
}
