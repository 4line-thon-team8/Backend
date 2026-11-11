package com.fivegrade.backend.report.repository;

import com.fivegrade.backend.report.entity.Report;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	/** 같은 사용자가 같은 키로 이미 저장했는지 확인한다. 최종 중복 방지는 DB 유니크 제약이 맡는다. */
	Optional<Report> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

	/** 마이페이지 목록용 최신순. idx_reports_user_created 인덱스를 쓴다. */
	List<Report> findByUserIdOrderByCreatedAtDesc(Long userId);

	/** 회원 탈퇴용. 추천 원서 행도 함께 지워진다. */
	long deleteByUserId(Long userId);

}
