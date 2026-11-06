package com.fivegrade.backend.auth.service;

import com.fivegrade.backend.auth.model.IssuedAccessToken;
import com.fivegrade.backend.auth.support.AccessTokenCodec;
import com.fivegrade.backend.user.entity.AuthToken;
import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.repository.AuthTokenRepository;
import com.fivegrade.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * DB 저장형 인증 토큰의 발급·유효성 확인·폐기를 담당한다.
 *
 * 로그인 식별자나 비밀번호 확인 방식에는 의존하지 않는다.
 * issue는 호출 측에서 사용자 인증을 완료한 뒤 호출해야 한다.
 *
 * 공개 API의 상태 코드와 오류 응답은 후속 인증 계층에서 결정한다.
 * 토큰 원문과 해시는 로그나 예외 메시지에 넣지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class AccessTokenService {

    private static final Duration TOKEN_LIFETIME =
            Duration.ofHours(2);

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final AccessTokenCodec codec;
    private final Clock clock;

    public AccessTokenService(
            UserRepository users,
            AuthTokenRepository tokens,
            AccessTokenCodec codec,
            @Qualifier("authTokenClock") Clock clock
    ) {
        this.users = users;
        this.tokens = tokens;
        this.codec = codec;
        this.clock = clock;
    }

    /**
     * 인증을 마친 기존 사용자에게 새 토큰을 발급한다.
     *
     * 이 메서드 자체는 비밀번호나 사용자 신원을 확인하지 않는다.
     * 요청 본문의 userId를 그대로 넘기는 공개 API로 연결하면 안 된다.
     */
    @Transactional
    public IssuedAccessToken issue(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId is required"
            );
        }

        User user = users.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "user does not exist"
                        )
                );

        String rawToken = codec.generate();
        String tokenHash = codec.hash(rawToken);

        // PostgreSQL 저장 정밀도에 맞춰 마이크로초 단위로 통일한다.
        Instant issuedAt = clock.instant()
                .truncatedTo(ChronoUnit.MICROS);

        Instant expiresAt = issuedAt.plus(TOKEN_LIFETIME);

        tokens.saveAndFlush(
                new AuthToken(
                        user,
                        tokenHash,
                        expiresAt
                )
        );

        return new IssuedAccessToken(
                rawToken,
                expiresAt
        );
    }

    /**
     * 저장되어 있고 만료되지 않은 토큰의 사용자 ID를 반환한다.
     *
     * 없거나 잘못된 형식이거나 만료됐다면 빈 결과를 반환한다.
     * 만료 시각과 현재 시각이 같아도 만료로 판정한다.
     * 유효성 확인으로 만료 시각을 변경하지 않는다.
     */
    public Optional<Long> findValidUserId(String rawToken) {
        if (!codec.hasValidFormat(rawToken)) {
            return Optional.empty();
        }

        return tokens.findByTokenHash(codec.hash(rawToken))
                .filter(token ->
                        clock.instant()
                                .isBefore(token.getExpiresAt())
                )
                .map(token -> token.getUser().getId());
    }

    /**
     * 지정된 토큰 하나를 폐기한다.
     *
     * 실제 삭제했으면 true, 형식이 잘못됐거나 없으면 false다.
     * 이 반환값을 공개 API의 응답 정책으로 직접 해석하지 않는다.
     */
    @Transactional
    public boolean revoke(String rawToken) {
        if (!codec.hasValidFormat(rawToken)) {
            return false;
        }

        return tokens.deleteByTokenHash(
                codec.hash(rawToken)
        ) > 0;
    }
}