package com.fivegrade.backend.auth.dto;

import com.fivegrade.backend.auth.validation.ValidPassword;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 가입·로그인 요청. toString에 이메일·비밀번호를 노출하지 않는다. */
@Schema(name = "AuthRequest")
public final class AuthRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    @Schema(description = "이메일 식별자. 앞뒤 공백 제거 후 소문자로 저장", maxLength = 254)
    private final String email;

    @NotBlank
    @ValidPassword
    @Schema(description = "15~64 Unicode 코드 포인트. 원문 그대로 사용",
            format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private final String password;

    @JsonCreator
    public AuthRequest(
            @JsonProperty("email") String email,
            @JsonProperty("password") String password
    ) {
        this.email = email == null ? null : email.trim();
        this.password = password;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("unknown request field");
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "AuthRequest{email=[REDACTED], password=[REDACTED]}";
    }
}
