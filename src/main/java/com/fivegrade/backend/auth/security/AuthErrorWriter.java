package com.fivegrade.backend.auth.security;

import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.common.error.ErrorResponse;
import com.fivegrade.backend.common.error.GlobalExceptionHandler;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 필터 오류도 MVC 공통 처리기와 동일한 code/message로 반환한다. */
@Component
public class AuthErrorWriter {

    private final JsonMapper json;
    private final GlobalExceptionHandler handler;

    public AuthErrorWriter(JsonMapper json, GlobalExceptionHandler handler) {
        this.json = json;
        this.handler = handler;
    }

    public void write(HttpServletResponse response, ErrorCode code)
            throws IOException {
        writeResponse(response, handler.handleBusiness(new BusinessException(code)));
    }

    public void unexpected(HttpServletResponse response, Exception exception)
            throws IOException {
        // 기존 처리기의 입력값을 제외한 안전한 로그 방식을 재사용한다.
        writeResponse(response, handler.handleUnexpected(exception));
    }

    private void writeResponse(
            HttpServletResponse response,
            ResponseEntity<ErrorResponse> result
    ) throws IOException {
        response.setStatus(result.getStatusCode().value());
        result.getHeaders().forEach((name, values) ->
                values.forEach(value -> response.addHeader(name, value))
        );
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), result.getBody());
    }
}
