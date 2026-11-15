package com.fivegrade.backend.user.repository;

import com.fivegrade.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 엔티티의 저장·조회·삭제를 담당한다.
 *
 * 이메일 조회 인자는 정규화된 값이어야 한다.
 * 외부 입력을 사용하는 조회는 UserLookupService를 통해 수행한다.
 *
 * existsByEmail로 사전 확인하더라도 동시 가입 요청은 발생할 수 있으므로,
 * 최종 중복 방지는 DB의 UNIQUE 제약을 기준으로 처리한다.
 */

public interface UserRepository
        extends JpaRepository<User, Long> {

    // 정규화된 이메일로 조회한다.
    Optional<User> findByEmail(String email);

    // 중복 여부 사전 확인용. DB UNIQUE 제약을 대체하지 않는다.
    boolean existsByEmail(String email);
}