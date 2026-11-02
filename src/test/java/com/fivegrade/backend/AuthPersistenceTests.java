package com.fivegrade.backend;

import com.fivegrade.backend.user.entity.AuthToken;
import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.repository.AuthTokenRepository;
import com.fivegrade.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 PostgreSQL에서 인증 DB 구조와 JPA 매핑을 검증하는 통합 테스트.
 *
 * 기존 TestcontainersConfiguration을 사용하므로 Docker가 실행 중이어야 한다.
 * 접속 정보는 Testcontainers의 별도 DB로 연결되며 개발용 DB를 사용하지 않는다.
 *
 * 검증 범위:
 * - V1·V2 마이그레이션과 IDENTITY 정책
 * - JPA 엔티티·Repository 저장 및 조회
 * - 이메일·토큰 해시 UNIQUE와 사용자 FK
 * - 사용자 삭제 시 토큰 CASCADE
 * - 기존 V1 데이터를 보존한 V2 업그레이드
 *
 * 일반 테스트는 트랜잭션 종료 시 롤백한다.
 * 업그레이드 테스트는 별도 스키마에서 실제 마이그레이션을 수행하고,
 * 해당 데이터는 테스트 컨테이너 종료 시 함께 제거된다.
 *
 * 모든 입력값은 테스트 전용 가상 값이다.
 */

@SpringBootTest(properties = {
        "spring.flyway.validate-migration-naming=true"
})
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthPersistenceTests {

    // 실제 비밀번호나 토큰이 아닌 테스트 전용 가상 값.
    private static final String PASSWORD_HASH =
            "test-only-encoded-password";

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);

    private static final Instant EXPIRES_AT =
            Instant.parse("2030-01-01T02:00:00Z");

    @Autowired
    private UserRepository users;

    @Autowired
    private AuthTokenRepository tokens;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    // 마이그레이션 순서와 두 테이블의 실제 ID 생성 정책을 DB 메타데이터로 확인한다.
    @Test
    void appliesV1AndV2AndUsesAlwaysIdentity() {
        List<String> versions = jdbc.queryForList("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = true
                ORDER BY installed_rank
                """, String.class);

        assertTrue(versions.containsAll(List.of("1", "2")));
        assertTrue(versions.indexOf("1") < versions.indexOf("2"));

        for (String table : List.of("users", "auth_tokens")) {
            String identityGeneration = jdbc.queryForObject("""
                    SELECT identity_generation
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = 'id'
                    """, String.class, table);

            assertEquals("ALWAYS", identityGeneration);
        }
    }

    // 영속성 컨텍스트를 비운 뒤 다시 조회해 실제 DB 저장과 매핑을 확인한다.
    @Test
    void persistsAndLoadsUserAndTokenThroughRepositories() {
        User user = users.saveAndFlush(
                new User("persist@example.com", PASSWORD_HASH)
        );

        AuthToken token = tokens.saveAndFlush(
                new AuthToken(user, HASH_A, EXPIRES_AT)
        );

        Long userId = user.getId();
        Long tokenId = token.getId();

        assertNotNull(userId);
        assertNotNull(tokenId);

        entityManager.clear();

        User foundUser = users.findByEmail(
                "persist@example.com"
        ).orElseThrow();

        AuthToken foundToken = tokens.findByTokenHash(
                HASH_A
        ).orElseThrow();

        assertEquals(userId, foundUser.getId());
        assertEquals(PASSWORD_HASH, foundUser.getPasswordHash());
        assertNotNull(foundUser.getCreatedAt());

        assertEquals(tokenId, foundToken.getId());
        assertEquals(userId, foundToken.getUser().getId());
        assertEquals(HASH_A, foundToken.getTokenHash());
        assertEquals(EXPIRES_AT, foundToken.getExpiresAt());

        assertTrue(users.existsByEmail("persist@example.com"));
        assertEquals(
                1,
                tokens.findAllByUser_Id(userId).size()
        );
    }

    // 같은 이메일의 중복 저장이 DB UNIQUE 제약으로 거부되는지 확인한다.
    @Test
    void rejectsDuplicateEmail() {
        users.saveAndFlush(
                new User("duplicate@example.com", PASSWORD_HASH)
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> users.saveAndFlush(
                        new User(
                                "duplicate@example.com",
                                PASSWORD_HASH
                        )
                )
        );
    }

    // 사용자가 달라도 동일한 토큰 해시를 중복 저장할 수 없어야 한다.
    @Test
    void rejectsDuplicateTokenHashAcrossUsers() {
        User first = users.saveAndFlush(
                new User("first@example.com", PASSWORD_HASH)
        );

        User second = users.saveAndFlush(
                new User("second@example.com", PASSWORD_HASH)
        );

        tokens.saveAndFlush(
                new AuthToken(first, HASH_A, EXPIRES_AT)
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> tokens.saveAndFlush(
                        new AuthToken(
                                second, HASH_A, EXPIRES_AT
                        )
                )
        );
    }

    // JPA 검증을 우회한 직접 INSERT에서도 DB FK가 잘못된 사용자 참조를 막아야 한다.
    @Test
    void rejectsTokenForMissingUser() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO auth_tokens
                            (user_id, token_hash, expires_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        """, -1L, HASH_A)
        );
    }

    // 엔티티 생성자를 거치지 않아도 DB가 잘못된 해시 형식을 거부해야 한다.
    @Test
    void databaseRejectsInvalidTokenHash() {
        User user = users.saveAndFlush(
                new User("invalid-hash@example.com", PASSWORD_HASH)
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO auth_tokens
                            (user_id, token_hash, expires_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        """, user.getId(), "not-a-sha256-hash")
        );
    }

    // 직접 SQL로 사용자를 삭제해 DB CASCADE를 검증한다.
    // 삭제 대상 사용자의 토큰만 제거되고 다른 사용자의 토큰은 유지되어야 한다.
    @Test
    void deletingUserCascadesOnlyTheirTokens() {
        User removedUser = users.saveAndFlush(
                new User("removed@example.com", PASSWORD_HASH)
        );

        User remainingUser = users.saveAndFlush(
                new User("remaining@example.com", PASSWORD_HASH)
        );

        tokens.saveAllAndFlush(List.of(
                new AuthToken(removedUser, HASH_A, EXPIRES_AT),
                new AuthToken(removedUser, HASH_B, EXPIRES_AT),
                new AuthToken(remainingUser, HASH_C, EXPIRES_AT)
        ));

        Long removedId = removedUser.getId();
        Long remainingId = remainingUser.getId();

        // JPA 연관관계 삭제가 아닌 실제 DB FK CASCADE를 검증한다.
        jdbc.update(
                "DELETE FROM users WHERE id = ?",
                removedId
        );

        entityManager.clear();

        assertFalse(users.existsById(removedId));
        assertTrue(tokens.findAllByUser_Id(removedId).isEmpty());
        assertTrue(tokens.findByTokenHash(HASH_A).isEmpty());
        assertTrue(tokens.findByTokenHash(HASH_B).isEmpty());

        assertTrue(users.existsById(remainingId));
        assertEquals(
                1,
                tokens.findAllByUser_Id(remainingId).size()
        );
        assertTrue(tokens.findByTokenHash(HASH_C).isPresent());
    }

    // 별도 스키마에 V1만 적용하고 가상 전형 데이터를 넣은 뒤 V2를 적용한다.
    // 기존 데이터 보존, 새 테이블 생성, 재실행 시 중복 적용 방지를 확인한다.
    // Flyway가 직접 트랜잭션을 관리하도록 테스트 트랜잭션은 사용하지 않는다.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void upgradesExistingV1WithoutLosingAdmissionData() {
        // Testcontainers DB 안의 별도 스키마를 사용한다.
        // 개발용 로컬 DB에는 접속하지 않는다.
        String schema = "auth_upgrade_test";

        Flyway v1 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .validateMigrationNaming(true)
                .target("1")
                .load();

        v1.migrate();

        jdbc.update("""
                INSERT INTO auth_upgrade_test.universities (name)
                VALUES (?)
                """, "TEST_ONLY_UNIVERSITY");

        assertNull(jdbc.queryForObject(
                "SELECT to_regclass(?)",
                String.class,
                schema + ".users"
        ));

        Flyway latest = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .validateMigrationNaming(true)
                .load();

        latest.migrate();
        latest.validate();

        assertEquals(
                1,
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM auth_upgrade_test.universities
                        WHERE name = ?
                        """, Integer.class, "TEST_ONLY_UNIVERSITY")
        );

        assertNotNull(jdbc.queryForObject(
                "SELECT to_regclass(?)",
                String.class,
                schema + ".users"
        ));

        assertNotNull(jdbc.queryForObject(
                "SELECT to_regclass(?)",
                String.class,
                schema + ".auth_tokens"
        ));

        // 스키마 생성 이력은 제외하고 버전이 있는 마이그레이션만 확인한다.
        List<String> versions = jdbc.queryForList("""
        SELECT version
        FROM auth_upgrade_test.flyway_schema_history
        WHERE success = true
          AND version IS NOT NULL
        ORDER BY installed_rank
        """, String.class);

        assertEquals(List.of("1", "2"), versions);

        // 이미 적용한 마이그레이션을 다시 실행해도 버전 이력이 늘어나지 않는다.
        latest.migrate();

        assertEquals(
                2,
                jdbc.queryForObject("""
                SELECT count(*)
                FROM auth_upgrade_test.flyway_schema_history
                WHERE success = true
                  AND version IS NOT NULL
                """, Integer.class)
        );
    }
}