package com.fivegrade.backend.auth.service;

import com.fivegrade.backend.user.entity.User;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** AuthService의 동일 DB 트랜잭션 안에서만 호출한다. */
@Repository
public class AuthAccountStore {

    private final JdbcTemplate jdbc;

    public AuthAccountStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 중복 가입은 DB UNIQUE로 판정한다.
     * UNIQUE 예외의 DB 상세 메시지에 이메일이 남는 것을 피한다.
     */
    public boolean insertIfAbsent(User user) {
        return !jdbc.query("""
                INSERT INTO users (email, password_hash, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT ON CONSTRAINT uk_users_email DO NOTHING
                RETURNING id
                """,
                (rs, row) -> rs.getLong("id"),
                user.getEmail(),
                user.getPasswordHash(),
                java.sql.Timestamp.from(user.getCreatedAt())
        ).isEmpty();
    }

    /** 삭제와 토큰 발급을 사용자 행 잠금으로 직렬화한다. */
    public boolean lockUser(Long userId) {
        return !jdbc.query("""
                SELECT id FROM users WHERE id = ? FOR UPDATE
                """, (rs, row) -> rs.getLong("id"), userId).isEmpty();
    }

    /** V3 reports.user_id는 CASCADE가 아니므로 리포트를 먼저 삭제한다. */
    public void deleteReports(Long userId) {
        jdbc.update("DELETE FROM reports WHERE user_id = ?", userId);
    }

    /** V2 FK ON DELETE CASCADE로 사용자의 모든 토큰도 함께 삭제된다. */
    public void deleteUser(Long userId) {
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
}
