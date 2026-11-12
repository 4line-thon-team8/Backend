package com.fivegrade.backend.auth.security;

import com.fivegrade.backend.auth.model.AuthenticatedUser;
import com.fivegrade.backend.auth.service.AccessTokenService;
import com.fivegrade.backend.common.error.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Authorization 헤더의 Bearer 토큰으로 사용자를 인증한다.
 *
 * SecurityConfig에서 직접 생성해 SecurityFilterChain에 등록한다.
 * 서블릿 필터로 중복 등록되지 않도록 @Component나 별도 필터 Bean으로 등록하지 않는다.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    public static final String RAW_TOKEN_ATTRIBUTE =
            "com.fivegrade.backend.auth.rawToken";

    private final AccessTokenService tokens;
    private final AuthErrorWriter errors;

    public BearerTokenFilter(AccessTokenService tokens, AuthErrorWriter errors) {
        this.tokens = tokens;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        // 공개 API는 Authorization 헤더가 남아 있어도 토큰을 검사하지 않는다.
        if ("OPTIONS".equals(request.getMethod())
                || PublicApiPaths.MATCHER.matches(request)) {
            chain.doFilter(request, response);
            return;
        }

        List<String> headers = Collections.list(
                request.getHeaders(HttpHeaders.AUTHORIZATION)
        );
        if (headers.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        if (headers.size() != 1) {
            errors.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        String header = headers.getFirst();
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            errors.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        String rawToken = header.substring(7);
        Optional<Long> userId;
        try {
            userId = tokens.findValidUserId(rawToken);
        } catch (Exception exception) {
            // DB 장애를 인증 실패로 바꾸지 않고 공통 500으로 처리한다.
            errors.unexpected(response, exception);
            return;
        }
        if (userId.isEmpty()) {
            errors.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(userId.get()), null, List.of()
                )
        );
        SecurityContextHolder.setContext(context);
        request.setAttribute(RAW_TOKEN_ATTRIBUTE, rawToken);
        try {
            chain.doFilter(request, response);
        } finally {
            request.removeAttribute(RAW_TOKEN_ATTRIBUTE);
            SecurityContextHolder.clearContext();
        }
    }
}
