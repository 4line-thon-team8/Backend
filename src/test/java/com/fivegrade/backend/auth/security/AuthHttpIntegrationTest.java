package com.fivegrade.backend.auth.security;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 API·SecurityFilterChain·PostgreSQL 연결을 함께 검증한다. */
@SpringBootTest(properties = "OPENAPI_ENABLED=false")
@Import(TestcontainersConfiguration.class)
class AuthHttpIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void signupLoginLogoutAndWithdrawalWorkThroughRealFilters() throws Exception {
        String email = "http-" + UUID.randomUUID() + "@example.com";
        String request = "{\"email\":\"" + email + "\",\"password\":\"test-only-password-2026\"}";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        Instant beforeLogin = Instant.now();
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getContentAsString();
        Instant afterLogin = Instant.now();
        var result = json.readTree(body);
        String token = result.path("accessToken").asText();
        Instant expiry = Instant.parse(result.path("expiresAt").asText());
        assertFalse(expiry.isBefore(beforeLogin.plus(Duration.ofHours(2))));
        assertFalse(expiry.isAfter(afterLogin.plus(Duration.ofHours(2))));
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        String secondBody = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = json.readTree(secondBody).path("accessToken").asText();
        mvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isNoContent());
        assertTrue(users.findByEmail(email).isEmpty());
        mvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void closedDocumentsRemain404ThroughRealSecurityChain() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
