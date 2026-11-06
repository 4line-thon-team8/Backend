package com.fivegrade.backend;

import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.repository.UserRepository;
import com.fivegrade.backend.user.service.UserLookupService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 PostgreSQL에서 이메일 정규화와 저장 제약을 검증한다.
 *
 * 각 테스트는 별도 트랜잭션에서 실행한 뒤 롤백한다.
 * 제약 위반 후에는 같은 트랜잭션으로 추가 SQL을 실행하지 않는다.
 * 모든 이메일과 비밀번호 해시는 테스트 전용 가상 값이다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EmailNormalizationPersistenceTests {

    private static final String PASSWORD_HASH =
            "test-only-encoded-password";

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
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO users
                            (email, password_hash)
                        VALUES (?, ?)
                        """, email, PASSWORD_HASH)
        );
    }

    @Test
    void databaseRejectsUnnormalizedUpdate() {
        Long id = users.saveAndFlush(
                new User(
                        "demo@example.com",
                        PASSWORD_HASH
                )
        ).getId();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        UPDATE users
                        SET email = ?
                        WHERE id = ?
                        """, "Demo@example.com", id)
        );
    }
}