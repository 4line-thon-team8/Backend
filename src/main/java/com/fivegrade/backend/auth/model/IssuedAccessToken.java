package com.fivegrade.backend.auth.model;

import java.time.Instant;

/**
 * 토큰 발급 직후 호출한 계층에 전달하는 내부 결과.
 *
 * 토큰 원문은 이 결과로 전달하고 DB에는 저장하지 않는다.
 * 공개 API 응답 형식은 후속 컨트롤러에서 별도로 정의한다.
 *
 * 객체를 실수로 출력해도 토큰 원문이 노출되지 않도록 한다.
 */
public final class IssuedAccessToken {

    private final String accessToken;
    private final Instant expiresAt;

    public IssuedAccessToken(
            String accessToken,
            Instant expiresAt
    ) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "IssuedAccessToken{accessToken=[REDACTED], "
                + "expiresAt=" + expiresAt + "}";
    }
}