package com.fivegrade.backend.report.dto;

import com.fivegrade.backend.report.entity.Report;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 마이페이지 목록의 리포트 한 줄. 추천 원서 요약은 필요해지면 추가한다.
 */
public record ReportSummary(
		@Schema(description = "저장 리포트 ID(UUID). 분석 응답의 reportId와 같은 값.")
		UUID id,
		@Schema(description = "저장 시각(UTC, ISO-8601)", example = "2026-11-15T09:30:00Z")
		Instant createdAt,
		@Schema(description = "인사·요약. 현재 신규 저장에서는 저장하지 않으므로 null.", nullable = true)
		String greetingSummary
) {

	public static ReportSummary from(Report report) {
		return new ReportSummary(report.getId(), report.getCreatedAt(), report.getGreetingSummary());
	}

}
