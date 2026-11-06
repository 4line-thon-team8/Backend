package com.fivegrade.backend;

import com.fivegrade.backend.auth.model.IssuedAccessToken;
import com.fivegrade.backend.auth.service.AccessTokenService;
import com.fivegrade.backend.auth.support.AccessTokenCodec;
import com.fivegrade.backend.user.entity.AuthToken;
import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.repository.AuthTokenRepository;
import com.fivegrade.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 PostgreSQL에서 토큰 발급·저장·조회·폐기를 검증한다.
 *
 * Testcontainers의 별도 DB를 사용하고 각 테스트는 롤백한다.
 * 이메일·비밀번호 해시는 테스트 전용 가상 값이다.
 * 토큰 원문이나 해시는 출력하지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccessTokenPersistenceTests {

    @Autowired
    private AccessTokenService service;

    @Autowired
    private AccessTokenCodec codec;

    @Autowired
    private UserRepository users;

    @Autowired
    private AuthTokenRepository tokens;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void issuesTokenAndStoresOnlyItsHash() {
        User user = createUser("issue@example.com");

        Instant before = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);

        IssuedAccessToken issued =
                service.issue(user.getId());

        Instant after = Instant.now();

        String rawToken = issued.getAccessToken();
        String hash = codec.hash(rawToken);

        entityManager.clear();

        AuthToken stored = tokens.findByTokenHash(hash)
                .orElseThrow();

        assertEquals(
                user.getId(),
                stored.getUser().getId()
        );

        assertEquals(hash, stored.getTokenHash());

        assertNotEquals(
                rawToken,
                stored.getTokenHash()
        );

        assertEquals(
                issued.getExpiresAt(),
                stored.getExpiresAt()
        );

        assertFalse(
                issued.getExpiresAt().isBefore(
                        before.plus(Duration.ofHours(2))
                )
        );

        assertFalse(
                issued.getExpiresAt().isAfter(
                        after.plus(Duration.ofHours(2))
                )
        );

        assertEquals(
                Optional.of(user.getId()),
                service.findValidUserId(rawToken)
        );

        assertFalse(
                issued.toString().contains(rawToken)
        );
    }

    @Test
    void rejectsWellFormedButUnknownToken() {
        String unknownToken = codec.generate();

        assertTrue(
                service.findValidUserId(
                        unknownToken
                ).isEmpty()
        );
    }

    @Test
    void rejectsExpiredDatabaseToken() {
        User user = createUser("expired@example.com");
        String rawToken = codec.generate();

        tokens.saveAndFlush(
                new AuthToken(
                        user,
                        codec.hash(rawToken),
                        Instant.now().minusSeconds(60)
                )
        );

        entityManager.clear();

        assertTrue(
                service.findValidUserId(
                        rawToken
                ).isEmpty()
        );
    }

    @Test
    void revokesOnlySpecifiedToken() {
        User user = createUser("revoke@example.com");

        IssuedAccessToken removed =
                service.issue(user.getId());

        IssuedAccessToken remaining =
                service.issue(user.getId());

        assertTrue(
                service.revoke(
                        removed.getAccessToken()
                )
        );

        entityManager.clear();

        assertTrue(
                service.findValidUserId(
                        removed.getAccessToken()
                ).isEmpty()
        );

        assertEquals(
                Optional.of(user.getId()),
                service.findValidUserId(
                        remaining.getAccessToken()
                )
        );

        assertFalse(
                service.revoke(
                        removed.getAccessToken()
                )
        );
    }

    @Test
    void deletedUserCannotBeAuthenticatedByOldToken() {
        User user = createUser("deleted@example.com");

        IssuedAccessToken issued =
                service.issue(user.getId());

        // DB의 기존 사용자 삭제 → 토큰 CASCADE도 함께 확인한다.
        jdbc.update(
                "DELETE FROM users WHERE id = ?",
                user.getId()
        );

        entityManager.clear();

        assertTrue(
                service.findValidUserId(
                        issued.getAccessToken()
                ).isEmpty()
        );
    }

    private User createUser(String email) {
        return users.saveAndFlush(
                new User(
                        email,
                        "test-only-encoded-password"
                )
        );
    }
}