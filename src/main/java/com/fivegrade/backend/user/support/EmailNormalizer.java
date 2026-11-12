package com.fivegrade.backend.user.support;

import java.util.Locale;

/**
 * 이메일 저장과 조회에 공통으로 사용하는 정규화 규칙.
 *
 * 앞뒤 공백을 제거하고 실행 환경과 무관하게 소문자로 변환한다.
 * 이메일 형식이나 가입 가능 여부를 결정하는 클래스는 아니다.
 *
 * 입력 이메일이 예외 메시지에 포함되지 않도록 한다.
 *
 * <p>정규화 예시:</p>
 * <pre>
 * 입력: "  Demo@Example.com  "
 * 결과: "demo@example.com"
 * </pre>
 */
public final class EmailNormalizer {

    private static final int MAX_LENGTH = 254;

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email is required");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);

        if (normalized.isBlank()
                || normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "email has an invalid length"
            );
        }

        return normalized;
    }
}
