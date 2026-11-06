package com.fivegrade.backend.auth.service;

import com.fivegrade.backend.auth.support.AccessTokenCodec;
import com.fivegrade.backend.user.entity.AuthToken;
import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.repository.AuthTokenRepository;
import com.fivegrade.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 고정된 서버 시각으로 만료 경계를 검증한다.
 * DB 접근은 모의 Repository로 대체한다.
 */
class AccessTokenServiceTest {

    private static final Instant NOW =
            Instant.parse("2030-01-01T00:00:00Z");

    private final UserRepository users =
            mock(UserRepository.class);

    private final AuthTokenRepository tokens =
            mock(AuthTokenRepository.class);

    private final AccessTokenCodec codec =
            new AccessTokenCodec();

    private final AccessTokenService service =
            new AccessTokenService(
                    users,
                    tokens,
                    codec,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void acceptsTokenBeforeExpiration() {
        String rawToken = storedToken(
                NOW.plusSeconds(1)
        );

        assertEquals(
                Optional.of(1L),
                service.findValidUserId(rawToken)
        );
    }

    @Test
    void rejectsTokenExactlyAtExpiration() {
        String rawToken = storedToken(NOW);

        assertTrue(
                service.findValidUserId(rawToken).isEmpty()
        );
    }

    @Test
    void rejectsExpiredToken() {
        String rawToken = storedToken(
                NOW.minusSeconds(1)
        );

        assertTrue(
                service.findValidUserId(rawToken).isEmpty()
        );
    }

    @Test
    void malformedTokenDoesNotQueryDatabase() {
        assertTrue(
                service.findValidUserId(null).isEmpty()
        );

        assertTrue(
                service.findValidUserId("invalid").isEmpty()
        );

        verifyNoInteractions(tokens, users);
    }

    private String storedToken(Instant expiresAt) {
        String rawToken = codec.generate();

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        AuthToken stored = new AuthToken(
                user,
                codec.hash(rawToken),
                expiresAt
        );

        when(tokens.findByTokenHash(codec.hash(rawToken)))
                .thenReturn(Optional.of(stored));

        return rawToken;
    }
}