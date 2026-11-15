package com.fivegrade.backend.recommendation.dto;

/**
 * 추천 카드의 근거 지표. 전형 DB에 적재된 값만 쓰며, 적재되지 않은 값은 null이다.
 *
 * 전년 충원 현황과 전년 입결 추세는 응답 형식이 정해지지 않아 이번 초안에서 뺐다.
 * 형식이 정해지면 필드를 추가한다.
 *
 * @param quota                  모집 인원
 * @param competitionRate        경쟁률
 * @param newOrMergedDepartment  학과 신설·통합 여부. 입결 변동 가능성이 있다는 뜻이다.
 * @param basisYears             예측에 쓴 정상 입결 연수. 0이면 정상 입결이 없어 불량 행으로 대신했거나 평가하지 못한 것이다.
 */
public record CardEvidence(
		Integer quota,
		Double competitionRate,
		Boolean newOrMergedDepartment,
		int basisYears
) {

	public CardEvidence {
		if (quota != null && quota < 0) {
			throw new IllegalArgumentException("quota must not be negative");
		}
		if (competitionRate != null && competitionRate < 0) {
			throw new IllegalArgumentException("competitionRate must not be negative");
		}
		if (basisYears < 0) {
			throw new IllegalArgumentException("basisYears must not be negative");
		}
	}

}
