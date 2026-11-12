package com.fivegrade.backend.report.dto;

import com.fivegrade.backend.report.entity.Report;
import java.time.Instant;
import java.util.UUID;

/**
 * 마이페이지 목록의 리포트 한 줄. 추천 원서 요약은 필요해지면 추가한다.
 */
public record ReportSummary(UUID id, Instant createdAt, String greetingSummary) {

	public static ReportSummary from(Report report) {
		return new ReportSummary(report.getId(), report.getCreatedAt(), report.getGreetingSummary());
	}

}
