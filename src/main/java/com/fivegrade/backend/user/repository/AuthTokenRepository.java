package com.fivegrade.backend.user.repository;

import com.fivegrade.backend.user.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 인증 토큰 해시의 저장·조회·삭제를 담당한다.
 *
 * 토큰 원문이 아닌 계산된 해시로 조회한다.
 * 조회 결과가 존재한다는 사실만으로 인증이 성공하는 것은 아니며,
 * 인증 계층에서 만료 시각과 사용자 상태를 함께 확인해야 한다.
 */
public interface AuthTokenRepository
        extends JpaRepository<AuthToken, Long> {

    // 요청 토큰의 SHA-256 해시로 조회한다. 만료 필터링은 이 메서드에 포함되지 않는다.
    Optional<AuthToken> findByTokenHash(String tokenHash);

    // AuthToken.user.id를 기준으로 해당 사용자의 모든 토큰을 조회한다.
    List<AuthToken> findAllByUser_Id(Long userId);
}