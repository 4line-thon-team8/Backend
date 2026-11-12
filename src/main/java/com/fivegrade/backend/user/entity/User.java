package com.fivegrade.backend.user.entity;

import com.fivegrade.backend.user.support.EmailNormalizer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * 계정 정보를 users 테이블에 매핑하는 엔티티.
 *
 * 비밀번호는 원문이 아닌 인코딩된 결과를 보관한다.
 * 이메일은 공통 정규화 규칙을 적용해서 보관한다.
 * 비밀번호 인코딩과 가입 정책 검증은 호출하는 계층에서 수행한다.
 *
 * 인증 토큰 목록은 이 엔티티에서 관리하지 않는다.
 * 사용자 삭제 시 토큰 정리는 DB의 ON DELETE CASCADE가 담당한다.
 *
 * API 응답에는 이 엔티티를 직접 노출하지 않고 별도 DTO를 사용한다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    /**
     * 새 계정을 생성한다.
     *
     * 이메일은 공통 규칙으로 정규화해서 저장한다.
     * passwordHash에는 호출 측에서 이미 인코딩한 값을 전달한다.
     * 이메일 형식과 가입 비밀번호 정책은 가입 계층에서 검증한다.
     */
    public User(String email, String passwordHash) {
        this.email = EmailNormalizer.normalize(email);
        this.passwordHash = requireText(
                passwordHash, 255, "passwordHash"
        );
        this.createdAt = Instant.now();
    }

    // 잘못된 값 자체가 예외 메시지에 노출되지 않도록 필드명만 사용한다.
    private static String requireText(
            String value,
            int maxLength,
            String field
    ) {
        Objects.requireNonNull(value, field + " is required");

        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " has an invalid length"
            );
        }

        return value;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
