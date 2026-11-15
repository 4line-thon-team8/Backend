package com.fivegrade.backend.report.dto;

import com.fivegrade.backend.report.entity.ReportRecommendation;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 저장된 추천 원서 한 장의 조회용 값. 전형 데이터 참조 ID 는 내부 값이라 내보내지 않는다.
 */
public record RecommendationDetail(
		@Schema(description = "추천 순서(지망 순위)", example = "1")
		int rank,
		@Schema(description = "대학 이름(분석 응답의 university)")
		String universityName,
		@Schema(description = "학과 이름(분석 응답의 department)")
		String deptName,
		@Schema(description = "전형 이름(분석 응답의 admissionType)")
		String trackName,
		@Schema(description = "합격 가능성 구간 라벨(SAFE, MATCH, REACH 등, 분석 응답의 category). 없으면 null.", nullable = true)
		String label,
		@Schema(description = "추천 이유 문장(문자열). 분석 응답의 reason(text, generationMethod 객체)과 다르다.")
		String reason
) {

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
