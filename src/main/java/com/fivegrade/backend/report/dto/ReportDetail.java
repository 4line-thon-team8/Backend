package com.fivegrade.backend.report.dto;

import com.fivegrade.backend.report.entity.Report;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 저장된 리포트 한 건의 조회용 값. 성적 숫자와 환산 결과는 저장하지 않으므로 담기지 않는다.
 */
public record ReportDetail(
		@Schema(description = "저장 리포트 ID(UUID). 분석 응답의 reportId와 같은 값.")
		UUID id,
		@Schema(description = "저장 시각(UTC, ISO-8601)", example = "2026-11-15T09:30:00Z")
		Instant createdAt,
		@Schema(description = "인사·요약. 현재 신규 저장에서는 저장하지 않으므로 null.", nullable = true)
		String greetingSummary,
		@Schema(description = "저장된 추천 원서와 추천 이유. 분석 응답과 필드 이름이 다르다.")
		List<RecommendationDetail> recommendations
) {

	public static ReportDetail from(Report report) {
		return new ReportDetail(
				report.getId(),
				report.getCreatedAt(),
				report.getGreetingSummary(),
				report.getRecommendations().stream().map(RecommendationDetail::from).toList());
	}

}
