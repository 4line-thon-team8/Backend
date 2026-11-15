package com.fivegrade.backend.auth.service;

import com.fivegrade.backend.auth.dto.AuthRequest;
import com.fivegrade.backend.auth.model.IssuedAccessToken;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.service.UserLookupService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserLookupService lookup;
    private AuthAccountStore accounts;
    private AccessTokenService tokens;
    private PasswordEncoder passwords;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        lookup = mock(UserLookupService.class);
        accounts = mock(AuthAccountStore.class);
        tokens = mock(AccessTokenService.class);
        passwords = mock(PasswordEncoder.class);
        when(passwords.encode(anyString())).thenReturn("encoded-test-hash");
        auth = new AuthService(lookup, accounts, tokens, passwords);
        clearInvocations(passwords);
    }

    @Test
    void signupNormalizesEmailAndDoesNotTrimPassword() {
        when(accounts.insertIfAbsent(any())).thenReturn(true);
        String password = "  password-with-spaces  ";
        auth.signup(new AuthRequest("  DEMO@Example.com  ", password));
        verify(passwords).encode(password);
        verify(accounts).insertIfAbsent(argThat(user ->
                user.getEmail().equals("demo@example.com")
                        && user.getPasswordHash().equals("encoded-test-hash")));
    }

    @Test
    void duplicateEmailUsesConflictCode() {
        when(accounts.insertIfAbsent(any())).thenReturn(false);
        BusinessException error = assertThrows(BusinessException.class, () ->
                auth.signup(new AuthRequest("demo@example.com", "test-password-2026")));
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, error.getErrorCode());
    }

    @Test
    void unknownAccountAndWrongPasswordUseSameCode() {
        when(lookup.findByEmail(anyString())).thenReturn(Optional.empty());
        BusinessException unknown = assertThrows(BusinessException.class, () ->
                auth.login(new AuthRequest("missing@example.com", "test-password-2026")));
        verify(passwords).matches("test-password-2026", "encoded-test-hash");
        when(lookup.findByEmail(anyString())).thenReturn(Optional.of(user()));
        BusinessException wrong = assertThrows(BusinessException.class, () ->
                auth.login(new AuthRequest("demo@example.com", "test-password-2026")));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, unknown.getErrorCode());
        assertEquals(unknown.getErrorCode(), wrong.getErrorCode());
        verifyNoInteractions(tokens);
    }

    @Test
    void successfulLoginLocksAccountBeforeIssuingToken() {
        when(lookup.findByEmail(anyString())).thenReturn(Optional.of(user()));
        when(passwords.matches(anyString(), anyString())).thenReturn(true);
        when(accounts.lockUser(7L)).thenReturn(true);
        Instant expiry = Instant.parse("2026-11-11T03:00:00Z");
        when(tokens.issue(7L)).thenReturn(new IssuedAccessToken("test-token", expiry));
        var response = auth.login(new AuthRequest("demo@example.com", "test-password-2026"));
        assertEquals("test-token", response.getAccessToken());
        assertEquals(expiry, response.getExpiresAt());
        InOrder order = inOrder(accounts, tokens);
        order.verify(accounts).lockUser(7L);
        order.verify(tokens).issue(7L);
        assertFalse(response.toString().contains("test-token"));
    }

    @Test
    void deletedAccountCannotReceiveNewToken() {
        when(lookup.findByEmail(anyString())).thenReturn(Optional.of(user()));
        when(passwords.matches(anyString(), anyString())).thenReturn(true);
        when(accounts.lockUser(7L)).thenReturn(false);
        assertThrows(BusinessException.class, () ->
                auth.login(new AuthRequest("demo@example.com", "test-password-2026")));
        verifyNoInteractions(tokens);
    }

    @Test
    void logoutRevokesOnlyProvidedToken() {
        auth.logout("only-this-token");
        verify(tokens).revoke("only-this-token");
        verifyNoMoreInteractions(tokens);
    }

    @Test
    void withdrawDeletesReportsBeforeUser() {
        when(accounts.lockUser(7L)).thenReturn(true);
        auth.withdraw(7L);
        InOrder order = inOrder(accounts);
        order.verify(accounts).lockUser(7L);
        order.verify(accounts).deleteReports(7L);
        order.verify(accounts).deleteUser(7L);
    }

    @Test
    void failedReportDeletionDoesNotProceedToUserDeletion() {
        when(accounts.lockUser(7L)).thenReturn(true);
        doThrow(new IllegalStateException("test failure"))
                .when(accounts).deleteReports(7L);
        assertThrows(IllegalStateException.class, () -> auth.withdraw(7L));
        verify(accounts, never()).deleteUser(anyLong());
    }

    private User user() {
        User user = new User("demo@example.com", "encoded-test-hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }
}
