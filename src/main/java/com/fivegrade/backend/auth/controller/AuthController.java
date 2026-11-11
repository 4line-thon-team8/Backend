package com.fivegrade.backend.auth.controller;

import com.fivegrade.backend.auth.dto.AuthRequest;
import com.fivegrade.backend.auth.dto.LoginResponse;
import com.fivegrade.backend.auth.security.BearerTokenFilter;
import com.fivegrade.backend.auth.service.AuthService;
import com.fivegrade.backend.common.error.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "회원가입", description = "가입 후 별도 로그인. 이메일 소유 확인은 하지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "계정 생성", content = @Content),
            @ApiResponse(responseCode = "400", description = "입력 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이메일 중복", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody AuthRequest request) {
        auth.signup(request);
        return ResponseEntity.status(201).build();
    }

    @Operation(summary = "로그인", description = "토큰은 발급 후 2시간 만료. refresh 및 자동 연장 없음.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 발급", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "동일한 문구의 인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }

    @Operation(summary = "로그아웃", description = "현재 요청의 토큰 하나만 폐기")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "폐기 완료", content = @Content),
            @ApiResponse(responseCode = "401", description = "유효한 토큰 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "접근 거부", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestAttribute(BearerTokenFilter.RAW_TOKEN_ATTRIBUTE) String rawToken
    ) {
        auth.logout(rawToken);
        return ResponseEntity.noContent().build();
    }
}
