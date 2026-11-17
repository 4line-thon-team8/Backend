package com.fivegrade.backend.recommendation.dto;

import java.util.List;

/**
 * 한 후보 전형에 대한 서류 적합도. 학종 후보에만 붙는다.
 *
 * 서류 수준(1~5)을 세 단계로 줄여 보여 준다. 수치 자체는 내리지 않는다.
 * 기준값은 검증 전 가설이므로 한 곳({@link #of})에서만 정한다.
 *
 * @param level 서류 적합도 단계
 * @param basis 판단에 크게 작용한 평가요소(ACADEMIC, MAJOR_FIT, COMMUNITY). 전형 비중 x 평균과의 거리가 큰 순, 최대 2개
 */
public record DocumentFit(Level level, List<String> basis) {

	/** 이 값 이상이면 HIGH */
	static final double HIGH_FROM = 4.0;
	/** 이 값 이하이면 LOW */
	static final double LOW_UNTIL = 2.0;

	public enum Level {
		HIGH, MID, LOW
	}

	public DocumentFit {
		if (level == null) {
			throw new IllegalArgumentException("level must not be null");
		}
		basis = basis == null ? List.of() : List.copyOf(basis);
	}

	public static DocumentFit of(double documentLevel, List<String> basis) {
		Level level = documentLevel >= HIGH_FROM ? Level.HIGH : documentLevel <= LOW_UNTIL ? Level.LOW : Level.MID;
		return new DocumentFit(level, basis);
	}

}
