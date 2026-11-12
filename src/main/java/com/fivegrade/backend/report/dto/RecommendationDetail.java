package com.fivegrade.backend.report.dto;

import com.fivegrade.backend.report.entity.ReportRecommendation;

/**
 * 저장된 추천 원서 한 장의 조회용 값. 전형 데이터 참조 ID 는 내부 값이라 내보내지 않는다.
 */
public record RecommendationDetail(
		int rank,
		String universityName,
		String deptName,
		String trackName,
		String label,
		String reason) {

	public static RecommendationDetail from(ReportRecommendation recommendation) {
		return new RecommendationDetail(
				recommendation.getRank(),
				recommendation.getUniversityName(),
				recommendation.getDeptName(),
				recommendation.getTrackName(),
				recommendation.getLabel(),
				recommendation.getReason());
	}

}
