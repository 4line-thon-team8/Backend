package com.fivegrade.backend.user.repository;

import com.fivegrade.backend.user.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 인증 토큰 해시의 저장·조회·삭제를 담당한다.
 *
 * 토큰 원문이 아닌 계산된 해시로 조회한다.
 * 토큰이 존재하더라도 만료 여부는 서비스에서 확인해야 한다.
 */
public interface AuthTokenRepository
        extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    List<AuthToken> findAllByUser_Id(Long userId);

    /**
     * 지정된 해시의 토큰만 삭제한다.
     * 반환값은 실제 삭제한 행 수다.
     * 트랜잭션이 있는 서비스에서 호출한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from AuthToken token
            where token.tokenHash = :tokenHash
            """)
    int deleteByTokenHash(
            @Param("tokenHash") String tokenHash
    );
}