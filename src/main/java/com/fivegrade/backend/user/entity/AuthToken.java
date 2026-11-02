package com.fivegrade.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * DB 저장형 인증 토큰의 해시와 만료 시각을 보관하는 엔티티.
 *
 * 클라이언트에 발급한 토큰 원문은 저장하지 않는다.
 * 인증 계층에서 계산한 SHA-256 해시를 받아 보관하며,
 * 요청 토큰의 해시를 계산해 저장된 값과 조회·대조하는 방식으로 사용한다.
 *
 * 토큰 생성, 해시 계산, 발급 후 2시간 만료 시각 설정,
 * 요청 시 만료 검증은 후속 인증 계층의 책임이다.
 *
 * 토큰 원문과 해시는 로그 또는 API 응답에 노출하지 않는다.
 */
@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    // 저장 표현을 소문자 16진수 64자리로 통일한다.
    // 형식만 검사하며, 실제로 안전한 난수 토큰에서 계산했는지는 보장하지 않는다.
    private static final Pattern SHA256_HEX =
            Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 토큰 여러 개가 사용자 한 명에 속한다.
    // 사용자 정보는 필요할 때 조회하며, 토큰에서 사용자로 삭제를 전파하지 않는다.
    // 사용자 삭제 → 토큰 삭제는 SQL의 FK ON DELETE CASCADE로 처리한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64
    )
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AuthToken() {
    }

    public AuthToken(
            User user,
            String tokenHash,
            Instant expiresAt
    ) {
        this.user = Objects.requireNonNull(
                user, "user is required"
        );

        Objects.requireNonNull(
                tokenHash, "tokenHash is required"
        );

        if (!SHA256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException(
                    "tokenHash must be a lowercase SHA-256 hex value"
            );
        }

        this.tokenHash = tokenHash;
        this.expiresAt = Objects.requireNonNull(
                expiresAt, "expiresAt is required"
        );
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}