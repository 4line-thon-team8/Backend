package com.fivegrade.backend.recommendation.dto;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

/**
 * 추천 서비스에 넘기는 입력.
 *
 * 평균은 반올림하기 전 값을 그대로 받는다. 합격 가능성 구간은 경계값 근처에서 판정이 갈리므로,
 * 화면 표시용으로 반올림한 값을 넣으면 판정이 달라질 수 있다.
 * 요청 DTO의 검증을 통과한 값이 들어오는 것이 정상이지만, 서비스를 직접 호출하는 경로에서도
 * 잘못된 입력이 조용히 계산되지 않도록 생성 시점에 다시 확인한다.
 * 예외 메시지에는 성적과 학과 코드 같은 입력 값을 넣지 않고 필드 이름만 쓴다.
 *
 * @param fiveGradeAverage  반올림 전 5등급 평균 (1.0~5.0)
 * @param desiredMajorCodes 희망 학과 코드 1~3개. 학과 사전에 있는 코드이며 중복이 없다.
 * @param trackPreference   선호 전형
 * @param includeReachCard  상향 지원 카드를 1장 이상 포함할지 여부
 * @param allowSimilarMajors 희망 학과 후보가 부족할 때 비슷한 학과로 넓혀도 되는지 여부
 */
public record RecommendationQuery(
		BigDecimal fiveGradeAverage,
		List<String> desiredMajorCodes,
		TrackPreference trackPreference,
		boolean includeReachCard,
		boolean allowSimilarMajors
) {

	public static final BigDecimal MIN_AVERAGE = BigDecimal.ONE;
	public static final BigDecimal MAX_AVERAGE = BigDecimal.valueOf(5);
	public static final int MAX_DESIRED_MAJORS = 3;

	public RecommendationQuery {
		if (fiveGradeAverage == null
				|| fiveGradeAverage.compareTo(MIN_AVERAGE) < 0
				|| fiveGradeAverage.compareTo(MAX_AVERAGE) > 0) {
			throw new IllegalArgumentException("fiveGradeAverage must be between 1 and 5");
		}
		if (desiredMajorCodes == null
				|| desiredMajorCodes.isEmpty()
				|| desiredMajorCodes.size() > MAX_DESIRED_MAJORS) {
			throw new IllegalArgumentException("desiredMajorCodes must have 1 to 3 items");
		}
		HashSet<String> distinct = new HashSet<>();
		for (String code : desiredMajorCodes) {
			if (code == null || code.isBlank()) {
				throw new IllegalArgumentException("desiredMajorCodes must not contain blank items");
			}
			distinct.add(code);
		}
		if (distinct.size() != desiredMajorCodes.size()) {
			throw new IllegalArgumentException("desiredMajorCodes must not contain duplicates");
		}
		if (trackPreference == null) {
			throw new IllegalArgumentException("trackPreference must not be null");
		}
		desiredMajorCodes = List.copyOf(desiredMajorCodes);
	}

}
