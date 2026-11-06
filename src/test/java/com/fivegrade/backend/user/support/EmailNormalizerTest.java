package com.fivegrade.backend.user.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DB 없이 이메일 공통 정규화 규칙을 검증한다.
 */
class EmailNormalizerTest {

    @Test
    void trimsAndLowercasesEmail() {
        assertEquals(
                "demo@example.com",
                EmailNormalizer.normalize(
                        "  Demo@Example.com  "
                )
        );
    }

    @Test
    void preservesAlreadyNormalizedEmail() {
        assertEquals(
                "demo@example.com",
                EmailNormalizer.normalize(
                        "demo@example.com"
                )
        );
    }

    @Test
    void normalizationIsIdempotent() {
        String once = EmailNormalizer.normalize(
                "  Demo@Example.com  "
        );

        assertEquals(
                once,
                EmailNormalizer.normalize(once)
        );
    }

    @Test
    void rejectsNullAndBlankEmail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EmailNormalizer.normalize(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> EmailNormalizer.normalize("   ")
        );
    }

    @Test
    void rejectsEmailLongerThanStorageLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EmailNormalizer.normalize(
                        "a".repeat(255)
                )
        );
    }
}