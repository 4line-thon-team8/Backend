package com.fivegrade.backend.user.repository;

import com.fivegrade.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 엔티티의 저장·조회·삭제를 담당한다.
 *
 * 기본 CRUD는 JpaRepository를 사용한다.
 * 이메일 정규화와 인증 정책은 호출하는 서비스에서 처리한다.
 *
 * existsByEmail로 사전 확인하더라도 동시 가입 요청은 발생할 수 있으므로,
 * 최종 중복 방지는 DB의 UNIQUE 제약을 기준으로 처리해야 한다.
 */

public interface UserRepository
        extends JpaRepository<User, Long> {

    // 가입·로그인 계층에서 정규화 정책을 적용한 이메일로 조회한다.
    Optional<User> findByEmail(String email);

    // 중복 여부 사전 확인용. DB UNIQUE 제약을 대체하지 않는다.
    boolean existsByEmail(String email);
}