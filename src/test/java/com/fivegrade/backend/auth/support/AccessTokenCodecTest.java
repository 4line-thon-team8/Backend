package com.fivegrade.backend.auth.support;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 토큰 표현과 SHA-256 결과를 DB 없이 검증한다.
 * 테스트에서 생성한 토큰도 로그로 출력하지 않는다.
 */
class AccessTokenCodecTest {

    private final AccessTokenCodec codec = new AccessTokenCodec();

    @Test
    void generatesBase64UrlTokenFrom32Bytes() {
        String token = codec.generate();

        assertTrue(codec.hasValidFormat(token));
        assertEquals(
                32,
                Base64.getUrlDecoder().decode(token).length
        );
        assertFalse(token.contains("="));
    }

    @Test
    void calculatesKnownSha256Value() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223"
                        + "b00361a396177a9cb410ff61f20015ad",
                codec.hash("abc")
        );
    }

    @Test
    void doesNotNormalizeTokenInput() {
        assertNotEquals(
                codec.hash("Token"),
                codec.hash("token")
        );

        assertNotEquals(
                codec.hash("token"),
                codec.hash(" token ")
        );
    }

    @Test
    void rejectsMissingOrMalformedTokenFormat() {
        assertFalse(codec.hasValidFormat(null));
        assertFalse(codec.hasValidFormat(""));
        assertFalse(codec.hasValidFormat("not-a-valid-token"));
        assertFalse(
                codec.hasValidFormat(
                        " " + "a".repeat(43)
                )
        );
    }
}