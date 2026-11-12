package com.fivegrade.backend.auth.service;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.auth.dto.AuthRequest;
import com.fivegrade.backend.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 리포트 삭제 이후 실패해도 DB 트랜잭션 전체가 롤백되는지 확인한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuthWithdrawalRollbackTest {

    @Autowired AuthService auth;
    @Autowired AccessTokenService tokens;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuthAccountStore accounts;

    @Test
    void accountDeletionFailureRollsBackReportDeletion() {
        String email = "rollback-" + UUID.randomUUID() + "@example.com";
        auth.signup(new AuthRequest(email, "test-only-password-2026"));
        Long userId = users.findByEmail(email).orElseThrow().getId();
        var token = auth.login(new AuthRequest(email, "test-only-password-2026"));
        UUID reportId = UUID.randomUUID();
        jdbc.update("INSERT INTO reports (id, user_id, idempotency_key) VALUES (?, ?, ?)",
                reportId, userId, "rollback-test");
        try {
            doThrow(new DataAccessResourceFailureException("test-only-failure"))
                    .when(accounts).deleteUser(userId);
            assertThrows(DataAccessResourceFailureException.class, () -> auth.withdraw(userId));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM reports WHERE id = ?", Integer.class, reportId));
            assertTrue(users.existsById(userId));
            assertTrue(tokens.findValidUserId(token.getAccessToken()).isPresent());
        } finally {
            reset(accounts);
            auth.withdraw(userId);
        }
    }
}
