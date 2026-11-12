package com.fivegrade.backend.auth.service;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.auth.dto.AuthRequest;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.user.repository.AuthTokenRepository;
import com.fivegrade.backend.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL・実インコーダ・既存トークンサービスの結合を確認する。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuthApiPersistenceTest {

    @Autowired AuthService auth;
    @Autowired AccessTokenService tokens;
    @Autowired UserRepository users;
    @Autowired AuthTokenRepository tokenRows;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void signupLoginLogoutAndWithdrawalDeleteOnlyOwnData() {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        String otherEmail = "auth-other-" + UUID.randomUUID() + "@example.com";
        String password = "test-only-password-2026";
        auth.signup(new AuthRequest("  " + email.toUpperCase() + "  ", password));
        auth.signup(new AuthRequest(otherEmail, password));
        var user = users.findByEmail(email).orElseThrow();
        var other = users.findByEmail(otherEmail).orElseThrow();
        assertNotEquals(password, user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("{argon2id-v1}"));
        assertTrue(passwords.matches(password, user.getPasswordHash()));

        var first = auth.login(new AuthRequest(email, password));
        var second = auth.login(new AuthRequest(email, password));
        var otherToken = auth.login(new AuthRequest(otherEmail, password));
        assertEquals(user.getId(), tokens.findValidUserId(first.getAccessToken()).orElseThrow());
        assertTrue(tokenRows.findAllByUser_Id(user.getId()).stream().noneMatch(row ->
                row.getTokenHash().equals(first.getAccessToken())));

        auth.logout(first.getAccessToken());
        assertTrue(tokens.findValidUserId(first.getAccessToken()).isEmpty());
        assertTrue(tokens.findValidUserId(second.getAccessToken()).isPresent());

        UUID reportId = UUID.randomUUID();
        UUID otherReportId = UUID.randomUUID();
        jdbc.update("INSERT INTO reports (id, user_id, idempotency_key) VALUES (?, ?, ?)",
                reportId, user.getId(), "auth-own-test");
        jdbc.update("INSERT INTO reports (id, user_id, idempotency_key) VALUES (?, ?, ?)",
                otherReportId, other.getId(), "auth-other-test");
        jdbc.update("""
                INSERT INTO report_recommendations
                    (report_id, rank, university_name, dept_name, track_name, reason)
                VALUES (?, 1, 'test-university', 'test-dept', 'test-track', 'test-reason')
                """, reportId);

        auth.withdraw(user.getId());
        // JDBC 삭제 이후 JPA 1차 캐시 대신 SQL로 실제 삭제 여부를 확인한다.
        assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", user.getId()));
        assertEquals(0, count("SELECT count(*) FROM reports WHERE id = ?", reportId));
        assertEquals(0, count("SELECT count(*) FROM report_recommendations WHERE report_id = ?", reportId));
        assertEquals(0, count("SELECT count(*) FROM auth_tokens WHERE user_id = ?", user.getId()));
        assertTrue(tokens.findValidUserId(second.getAccessToken()).isEmpty());
        assertEquals(1, count("SELECT count(*) FROM users WHERE id = ?", other.getId()));
        assertEquals(1, count("SELECT count(*) FROM reports WHERE id = ?", otherReportId));
        assertTrue(tokens.findValidUserId(otherToken.getAccessToken()).isPresent());
    }

    @Test
    void duplicateSignupIs409BusinessCodeAndKeepsOneAccount() {
        String email = "auth-duplicate-" + UUID.randomUUID() + "@example.com";
        try {
            auth.signup(new AuthRequest(email, "test-only-password-2026"));
            BusinessException error = assertThrows(BusinessException.class, () ->
                    auth.signup(new AuthRequest(email.toUpperCase(), "test-only-password-2026")));
            assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, error.getErrorCode());
            assertEquals(1, count("SELECT count(*) FROM users WHERE email = ?", email));
        } finally {
            users.findByEmail(email).ifPresent(user -> auth.withdraw(user.getId()));
        }
    }

    private int count(String sql, Object value) {
        return jdbc.queryForObject(sql, Integer.class, value);
    }
}
