package com.fivegrade.backend.recommendation.dto;

/**
 * 추천 서비스가 받는 선호 전형.
 *
 * 분석 요청의 선호 전형(PreferredTrack)과 값은 같지만 따로 둔다.
 * 추천 패키지가 요청 DTO 패키지를 가져다 쓰면 simulation ↔ recommendation 이 서로를 참조하게 되기 때문이다.
 * 요청 값에서 이 값으로 바꾸는 일은 SimulationService 의 책임이다.
 */
public enum TrackPreference {

	/** 교과 전형 */
	SUBJECT,

	/** 학생부종합 전형 */
	HOLISTIC,

	/** 상관없음 */
	ANY

}
