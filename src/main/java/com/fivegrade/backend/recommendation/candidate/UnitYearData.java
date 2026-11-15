package com.fivegrade.backend.recommendation.candidate;

/**
 * 모집단위의 한 해 정보 중 추천에 쓰는 값만 옮긴 것. 엔티티를 추천 계산 코드로 가져가지 않으려고 따로 둔다.
 * 등급 값은 9등급 원본이며, 적재되지 않은 값은 null이다.
 *
 * @param year            연도
 * @param quota           모집 인원
 * @param competitionRate 경쟁률
 * @param newOrMerged     학과 신설·통합 여부
 * @param cut50           50%컷(9등급)
 * @param cut70           70%컷(9등급)
 */
public record UnitYearData(
		int year,
		Integer quota,
		Double competitionRate,
		Boolean newOrMerged,
		Double cut50,
		Double cut70
) {

	/** 50%컷과 70%컷이 모두 있어 컷 예측에 쓸 수 있는 연도인지 여부 */
	public boolean hasBothCuts() {
		return cut50 != null && cut70 != null;
	}

}
