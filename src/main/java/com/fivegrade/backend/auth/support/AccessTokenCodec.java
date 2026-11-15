package com.fivegrade.backend.auth.support;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 인증 토큰 생성과 저장용 해시 계산을 담당한다.
 *
 * 토큰은 32바이트 난수를 Base64URL 문자열로 표현한다.
 * DB에는 토큰 원문 대신 SHA-256 해시를 저장한다.
 *
 * 이 클래스의 SHA-256은 랜덤 토큰용이며,
 * 사용자 비밀번호 저장에는 사용하지 않는다.
 */
@Component
public class AccessTokenCodec {

    private static final int TOKEN_BYTES = 32;

    // 32바이트를 패딩 없는 Base64URL로 표현하면 43자가 된다.
    private static final Pattern TOKEN_FORMAT =
            Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * 이 서비스에서 발급하는 토큰의 형식인지 확인한다.
     * 토큰의 실제 존재·만료 여부는 서비스와 DB에서 확인한다.
     */
    public boolean hasValidFormat(String rawToken) {
        return rawToken != null
                && TOKEN_FORMAT.matcher(rawToken).matches();
    }

    /**
     * 입력을 변경하지 않고 SHA-256 해시를 계산한다.
     * 토큰은 이메일과 달리 trim이나 소문자 변환을 하지 않는다.
     */
    public String hash(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("token is required");
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", e
            );
        }
    }
}