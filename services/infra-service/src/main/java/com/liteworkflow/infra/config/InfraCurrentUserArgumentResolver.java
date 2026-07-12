package com.liteworkflow.infra.config;

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
public class InfraCurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    private final JwtTokenService tokens;
    public InfraCurrentUserArgumentResolver(JwtTokenService tokens) { this.tokens = tokens; }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
            NativeWebRequest webRequest, WebDataBinderFactory factory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        try {
            if (request == null) throw unauthorized();
            CurrentUser user = tokens.parseAccessToken(JwtTokenService.removeBearerPrefix(
                    request.getHeader(HttpHeaders.AUTHORIZATION)));
            String forwarded = request.getHeader("X-User-Id");
            if (forwarded != null && !forwarded.equals(user.userId().toString())) throw unauthorized();
            return user;
        } catch (InvalidTokenException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private BizException unauthorized() {
        return new BizException(CommonErrorCode.UNAUTHORIZED, "Trusted gateway identity is required");
    }
}
