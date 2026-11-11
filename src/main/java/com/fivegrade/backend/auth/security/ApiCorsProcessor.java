package com.fivegrade.backend.auth.security;

import com.fivegrade.backend.common.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.io.IOException;

/** CORS 거부도 텍스트 대신 공통 오류 JSON으로 반환한다. */
public class ApiCorsProcessor extends DefaultCorsProcessor {

    private final AuthErrorWriter errors;

    public ApiCorsProcessor(AuthErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    public boolean processRequest(
            CorsConfiguration configuration,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        boolean allowed = super.processRequest(configuration, request, response);
        if (!allowed) {
            errors.write(response, ErrorCode.FORBIDDEN);
        }
        return allowed;
    }

    @Override
    protected void rejectRequest(ServerHttpResponse response) throws IOException {
        // 본문 쓰기와 flush는 AuthErrorWriter가 담당한다.
        response.setStatusCode(HttpStatus.FORBIDDEN);
    }
}
