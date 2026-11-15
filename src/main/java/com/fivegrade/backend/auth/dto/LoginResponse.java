package com.fivegrade.backend.auth.dto;

import com.fivegrade.backend.auth.model.IssuedAccessToken;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "LoginResponse")
public final class LoginResponse {

    private final String accessToken;
    private final Instant expiresAt;

    public LoginResponse(IssuedAccessToken issued) {
        this.accessToken = issued.getAccessToken();
        this.expiresAt = issued.getExpiresAt();
    }

    @Schema(description = "DB 저장형 랜덤 Bearer 토큰. FE 메모리에만 보관")
    public String getAccessToken() {
        return accessToken;
    }

    @Schema(description = "발급 후 2시간의 절대 만료 시각. UTC", format = "date-time")
    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "LoginResponse{accessToken=[REDACTED], expiresAt=" + expiresAt + "}";
    }
}
