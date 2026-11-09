package com.fivegrade.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testcontainers DB의 별도 스키마에서 V3 → V4를 검증한다.
 *
 * 기존 사용자 데이터 보존, CHECK 제약 추가,
 * 마이그레이션 재실행 시 중복 적용 방지를 확인한다.
 *
 * 대상 버전을 고정해 이후 V5가 추가돼도 검증 범위를 유지한다.
 * 개발용 DB에는 접속하지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EmailNormalizationMigrationTests {

    private static final String SCHEMA =
            "email_v4_upgrade_test";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(2)
    void upgradesV3ToV4WithoutLosingUser() {
        // 별도 스키마에 V3까지 적용한다.
        Flyway v3 = flywayTo("3");
        v3.migrate();

        // V4 적용 전에 정규화된 가상 사용자를 저장한다.
        Long userId = jdbc.queryForObject("""
                INSERT INTO email_v4_upgrade_test.users
                    (email, password_hash)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                "migration@example.com",
                "test-only-encoded-password"
        );

        Flyway v4 = flywayTo("4");
        v4.migrate();
        v4.validate();

        // V4를 적용해도 기존 사용자가 유지되어야 한다.
        assertEquals(
                "migration@example.com",
                jdbc.queryForObject("""
                        SELECT email
                        FROM email_v4_upgrade_test.users
                        WHERE id = ?
                        """, String.class, userId)
        );

        // 이메일 CHECK가 실제로 추가되고 검증됐는지 확인한다.
        assertEquals(
                1,
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM pg_constraint
                        WHERE conrelid =
                            'email_v4_upgrade_test.users'::regclass
                          AND conname =
                            'ck_users_email_normalized'
                          AND contype = 'c'
                          AND convalidated
                        """, Integer.class)
        );

        // 재실행해도 V4의 적용 이력은 늘어나지 않는다.
        v4.migrate();

        assertEquals(
                1,
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM email_v4_upgrade_test.flyway_schema_history
                        WHERE version = '4'
                          AND success = true
                        """, Integer.class)
        );
    }
    @BeforeEach
    void resetTestSchema() {
        dropTestSchema();
    }

    @AfterEach
    void cleanUpTestSchema() {
        dropTestSchema();
    }

    private void dropTestSchema() {
        // Testcontainers의 고정된 테스트 전용 스키마에만 실행한다.
        jdbc.execute(
                "DROP SCHEMA IF EXISTS "
                        + SCHEMA
                        + " CASCADE"
        );
    }

    private Flyway flywayTo(String version) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .createSchemas(true)
                .validateMigrationNaming(true)
                .target(version)
                .load();
    }
}