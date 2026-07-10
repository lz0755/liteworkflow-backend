package com.liteworkflow.gateway.security;

import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.common.security.jwt.InvalidTokenException;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.gateway.trace.GatewayHeaders;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public final class JwtAuthenticationWebFilter implements WebFilter {

    public static final String CURRENT_USER_ATTRIBUTE = JwtAuthenticationWebFilter.class.getName() + ".currentUser";

    private final JwtTokenService jwtTokenService;
    private final SecurityResponseWriter responseWriter;

    public JwtAuthenticationWebFilter(JwtTokenService jwtTokenService, SecurityResponseWriter responseWriter) {
        this.jwtTokenService = jwtTokenService;
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        List<String> authorizationValues = exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION);
        if (authorizationValues.isEmpty()) {
            return chain.filter(exchange);
        }
        if (authorizationValues.size() != 1) {
            return responseWriter.write(
                    exchange, CommonErrorCode.UNAUTHORIZED, "Invalid or expired access token");
        }

        final CurrentUser currentUser;
        final String token;
        try {
            token = JwtTokenService.removeBearerPrefix(authorizationValues.getFirst());
            currentUser = jwtTokenService.parseAccessToken(token);
        } catch (InvalidTokenException exception) {
            return responseWriter.write(
                    exchange, CommonErrorCode.UNAUTHORIZED, "Invalid or expired access token");
        }

        var authorities = currentUser.roles().stream()
                .sorted(Comparator.naturalOrder())
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                currentUser.username(), null, authorities);
        authentication.setDetails(currentUser);

        String roles = currentUser.roles().stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
        ServerWebExchange authenticatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    // The trace filter has already removed all client-supplied internal headers.
                    headers.set(GatewayHeaders.USER_ID, currentUser.userId().toString());
                    headers.set(GatewayHeaders.USERNAME, currentUser.username());
                    headers.set(GatewayHeaders.USER_ROLES, roles);
                    headers.setBearerAuth(token);
                }))
                .build();
        authenticatedExchange.getAttributes().put(CURRENT_USER_ATTRIBUTE, currentUser);

        return chain.filter(authenticatedExchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }
}
