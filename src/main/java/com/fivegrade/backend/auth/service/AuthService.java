package com.fivegrade.backend.auth.service;

import com.fivegrade.backend.auth.dto.AuthRequest;
import com.fivegrade.backend.auth.dto.LoginResponse;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.service.UserLookupService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이메일·비밀번호 방식의 계정 API 구현안. */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserLookupService lookup;
    private final AuthAccountStore accounts;
    private final AccessTokenService tokens;
    private final PasswordEncoder passwords;
    private final String dummyPasswordHash;

    public AuthService(
            UserLookupService lookup,
            AuthAccountStore accounts,
            AccessTokenService tokens,
            PasswordEncoder passwords
    ) {
        this.lookup = lookup;
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwords = passwords;
        // 없는 계정도 해시 비교를 수행한다. 실제 계정 비밀번호가 아니다.
        this.dummyPasswordHash = passwords.encode("dummy-only-passphrase-for-timing");
    }

    @Transactional
    public void signup(AuthRequest request) {
        User user = new User(
                request.getEmail(), passwords.encode(request.getPassword())
        );
        if (!accounts.insertIfAbsent(user)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Transactional
    public LoginResponse login(AuthRequest request) {
        User user = lookup.findByEmail(request.getEmail()).orElse(null);
        String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean matches = passwords.matches(request.getPassword(), hash);
        if (user == null || !matches) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        if (!accounts.lockUser(user.getId())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        return new LoginResponse(tokens.issue(user.getId()));
    }

    @Transactional
    public void logout(String rawToken) {
        // 인증 이후 다른 요청에서 이미 폐기했더라도 삭제 완료 상태는 같다.
        tokens.revoke(rawToken);
    }

    @Transactional
    public void withdraw(Long userId) {
        if (!accounts.lockUser(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        accounts.deleteReports(userId);
        accounts.deleteUser(userId);
    }
}
