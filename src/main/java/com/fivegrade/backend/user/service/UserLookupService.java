package com.fivegrade.backend.user.service;

import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.repository.UserRepository;
import com.fivegrade.backend.user.support.EmailNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 이메일 기반 사용자 조회에 공통 정규화 규칙을 적용한다.
 *
 * 가입 중복 확인과 로그인 조회에서 같은 규칙을 사용하기 위한 서비스다.
 * 비밀번호 확인이나 토큰 발급은 담당하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class UserLookupService {

    private final UserRepository users;

    public UserLookupService(UserRepository users) {
        this.users = users;
    }

    public Optional<User> findByEmail(String email) {
        return users.findByEmail(
                EmailNormalizer.normalize(email)
        );
    }

    public boolean existsByEmail(String email) {
        return users.existsByEmail(
                EmailNormalizer.normalize(email)
        );
    }
}