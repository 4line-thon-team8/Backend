package com.fivegrade.backend.auth.security;

import com.fivegrade.backend.auth.config.SecurityConfig;
import com.fivegrade.backend.auth.controller.AuthController;
import com.fivegrade.backend.auth.dto.LoginResponse;
import com.fivegrade.backend.auth.model.AuthenticatedUser;
import com.fivegrade.backend.auth.model.IssuedAccessToken;
import com.fivegrade.backend.auth.service.AccessTokenService;
import com.fivegrade.backend.auth.service.AuthService;
import com.fivegrade.backend.common.error.GlobalExceptionHandler;
import com.fivegrade.backend.user.controller.UserController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 SecurityFilterChain을 포함하되 DB는 사용하지 않는다. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AuthHttpSecurityTest.Config.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class AuthHttpSecurityTest {

    @Test
    void publicDictionariesIgnoreStaleTokens() throws Exception {
        for (String path : new String[]{"/api/majors", "/api/subjects"}) {
            mvc.perform(get(path)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            HttpHeaders.CACHE_CONTROL, "no-store"));
        }

        verify(tokens, never()).findValidUserId(anyString());
    }

    @Test
    void signupAndLoginIgnoreStaleTokens() throws Exception {
        when(auth.login(any())).thenReturn(
                new LoginResponse(new IssuedAccessToken(
                        "response-token",
                        Instant.parse("2026-11-11T03:00:00Z")
                ))
        );

        mvc.perform(post("/api/auth/signup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("response-token"));

        verify(tokens, never()).findValidUserId(anyString());
    }

    @Test
    void protectedApisStillRejectStaleTokens() throws Exception {
        mvc.perform(get("/api/simulations/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isUnauthorized());

        // 공개 사전 경로라도 GET 이외의 메서드는 공개 대상이 아니다.
        mvc.perform(post("/api/majors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthErrorWriter.class, GlobalExceptionHandler.class,
            AuthController.class, UserController.class, ProbeController.class})
    static class Config {
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean AccessTokenService accessTokenService() { return mock(AccessTokenService.class); }
        @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/simulations/probe")
        Map<String, Long> simulation(@AuthenticationPrincipal AuthenticatedUser user) {
            return Map.of("userId", user.id());
        }

        @GetMapping("/api/reports/security-denied")
        void securityDenied() {
            throw new AccessDeniedException("private-denial-detail");
        }

        @GetMapping("/api/reports/other-owner")
        void otherOwner(@AuthenticationPrincipal AuthenticatedUser user) {
            user.requireOwner(99L);
        }

        @GetMapping({"/api/majors", "/api/subjects"})
        Map<String, String> dictionary() {
            return Map.of("result", "ok");
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired AuthService auth;
    @Autowired AccessTokenService tokens;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(auth, tokens);
        when(tokens.findValidUserId("valid-token")).thenReturn(Optional.of(7L));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void protectedApiRequiresBearerAndReturnsTwoFields() throws Exception {
        mvc.perform(post("/api/simulations"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidTokenDoesNotReachController() throws Exception {
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(auth);
    }

    @Test
    void duplicateOrWrongSchemeHeadersAreRejected() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token", "Bearer other"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Basic example"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenProvidesServerVerifiedUserId() throws Exception {
        mvc.perform(get("/api/simulations/probe")
                        .header(HttpHeaders.AUTHORIZATION, "bEaReR valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    void logoutRevokesCurrentTokenAndReturnsEmpty204() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(auth).logout("valid-token");
    }

    @Test
    void withdrawalUsesAuthenticatedUserAndNoBody() throws Exception {
        mvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isNoContent());
        verify(auth).withdraw(7L);
    }

    @Test
    void signupIsPublicAndLoginReturnsOnlyContractFields() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isCreated()).andExpect(content().string(""));
        when(auth.login(any())).thenReturn(new LoginResponse(new IssuedAccessToken(
                "response-token", Instant.parse("2026-11-11T03:00:00Z"))));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.accessToken").value("response-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-11T03:00:00Z"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void invalidInputAndUnknownFieldsReturn400WithoutValues() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"secret-invalid-email\",\"password\":\"short-secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(content().string(not(containsString("secret-invalid-email"))))
                .andExpect(content().string(not(containsString("short-secret"))));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials().replace("}", ",\"userId\":7}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerMismatchIs403WithCommonShape() throws Exception {
        mvc.perform(get("/api/reports/other-owner")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void mvcSecurityExceptionAlsoUses403InsteadOf500() throws Exception {
        mvc.perform(get("/api/reports/security-denied")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString("private-denial-detail"))));
    }

    @Test
    void tokenStoreFailureIs500WithoutInternalMessage() throws Exception {
        when(tokens.findValidUserId("db-failure")).thenThrow(
                new IllegalStateException("private-db-detail"));
        mvc.perform(get("/api/simulations/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer db-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("private-db-detail"))));
    }

    @Test
    void preflightIsHandledBeforeAuthentication() throws Exception {
        mvc.perform(options("/api/simulations")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        verifyNoInteractions(tokens);
    }

    @Test
    void authenticationFailureAlsoHasCorsHeaders() throws Exception {
        mvc.perform(post("/api/simulations")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void rejectedOriginUsesCommon403Shape() throws Exception {
        mvc.perform(options("/api/simulations")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String credentials() {
        return "{\"email\":\"demo@example.com\",\"password\":\"test-password-2026\"}";
    }
}
