package com.fivegrade.backend.report.dto;

import com.fivegrade.backend.report.entity.Report;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 저장된 리포트 한 건의 조회용 값. 성적 숫자와 환산 결과는 저장하지 않으므로 담기지 않는다.
 */
public record ReportDetail(
		UUID id,
		Instant createdAt,
		String greetingSummary,
		List<RecommendationDetail> recommendations) {

	public static ReportDetail from(Report report) {
		return new ReportDetail(
				report.getId(),
				report.getCreatedAt(),
				report.getGreetingSummary(),
				report.getRecommendations().stream().map(RecommendationDetail::from).toList());
	}

}
