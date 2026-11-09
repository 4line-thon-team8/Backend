package com.fivegrade.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.repository.UserRepository;
import com.fivegrade.backend.user.service.UserLookupService;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 PostgreSQL에서 이메일 정규화와 저장 제약을 검증한다.
 *
 * 각 테스트는 별도 트랜잭션에서 실행한 뒤 롤백한다.
 * 제약 위반 후에는 같은 트랜잭션으로 추가 SQL을 실행하지 않는다.
 *
 * 직접 SQL로 정규화되지 않은 이메일을 저장하면
 * 이메일 정규화 CHECK 제약으로 거부되는지 확인한다.
 *
 * 모든 이메일과 비밀번호 해시는 테스트 전용 가상 값이다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EmailNormalizationPersistenceTests {

    private static final String PASSWORD_HASH =
            "test-only-encoded-password";

    private static final String CHECK_VIOLATION_SQL_STATE =
            "23514";

    private static final String NORMALIZED_EMAIL_CONSTRAINT =
            "ck_users_email_normalized";

    @Autowired
    private UserRepository users;

    @Autowired
    private UserLookupService userLookup;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesNormalizedEmail() {
        Long id = users.saveAndFlush(
                new User(
                        "  Demo@Example.com  ",
                        PASSWORD_HASH
                )
        ).getId();

        entityManager.clear();

        User stored = users.findById(id).orElseThrow();

        assertEquals(
                "demo@example.com",
                stored.getEmail()
        );
    }

    @Test
    void findsUserUsingSameNormalizationRule() {
        Long id = users.saveAndFlush(
                new User(
                        "demo@example.com",
                        PASSWORD_HASH
                )
        ).getId();

        entityManager.clear();

        User found = userLookup.findByEmail(
                "  DEMO@Example.com  "
        ).orElseThrow();

        assertEquals(id, found.getId());

        assertTrue(
                userLookup.existsByEmail(
                        "  Demo@EXAMPLE.COM  "
                )
        );
    }

    @Test
    void rejectsDuplicateAfterNormalization() {
        users.saveAndFlush(
                new User(
                        "demo@example.com",
                        PASSWORD_HASH
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> users.saveAndFlush(
                        new User(
                                "  DEMO@Example.com  ",
                                PASSWORD_HASH
                        )
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Demo@example.com",
            " demo@example.com",
            "demo@example.com "
    })
    void databaseRejectsUnnormalizedInsert(String email) {
        DataIntegrityViolationException exception =
                assertThrows(
                        DataIntegrityViolationException.class,
                        () -> jdbc.update("""
                                INSERT INTO users
                                    (email, password_hash)
                                VALUES (?, ?)
                                """,
                                email,
                                PASSWORD_HASH
                        )
                );

        assertNormalizedEmailConstraint(exception);
    }

    @Test
    void databaseRejectsUnnormalizedUpdate() {
        Long id = users.saveAndFlush(
                new User(
                        "demo@example.com",
                        PASSWORD_HASH
                )
        ).getId();

        DataIntegrityViolationException exception =
                assertThrows(
                        DataIntegrityViolationException.class,
                        () -> jdbc.update("""
                                UPDATE users
                                SET email = ?
                                WHERE id = ?
                                """,
                                "Demo@example.com",
                                id
                        )
                );

        assertNormalizedEmailConstraint(exception);
    }

    /**
     * 이메일 정규화 CHECK 제약 때문에 실패했는지 확인한다.
     *
     * 예외 정보만 검사하며, 제약 위반 이후 추가 SQL은 실행하지 않는다.
     * 예외 메시지 원문을 로그로 출력하지 않는다.
     */
    private void assertNormalizedEmailConstraint(
            DataIntegrityViolationException exception
    ) {
        SQLException cause = assertInstanceOf(
                SQLException.class,
                exception.getMostSpecificCause()
        );

        assertEquals(
                CHECK_VIOLATION_SQL_STATE,
                cause.getSQLState(),
                "CHECK 제약 위반이어야 한다"
        );

        assertTrue(
                cause.getMessage() != null
                        && cause.getMessage()
                        .contains(NORMALIZED_EMAIL_CONSTRAINT),
                "이메일 정규화 CHECK 제약 위반이어야 한다"
        );
    }
}