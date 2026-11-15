package com.fivegrade.backend.recommendation.dto;

/**
 * 추천 카드가 어떤 경로로 후보에 올랐는지.
 *
 * 후보가 6장에 못 미치면 비슷한 학과, 인접 전형, 자율전공 순으로 넓혀 채운다.
 * 사용자가 고른 것과 다른 후보가 섞였을 때 리포트와 화면이 그 이유를 설명할 수 있도록 남긴다.
 */
public enum CandidateKind {

	/** 사용자가 고른 희망 학과 그대로 */
	DESIRED_MAJOR,

	/** 비슷한 학과로 넓혀 채운 후보 (allowSimilarMajors 가 켜져 있을 때만) */
	SIMILAR_MAJOR,

	/** 선호 전형이 아닌 인접 전형으로 넓혀 채운 후보 */
	OTHER_TRACK,

	/** 자율전공·무전공 등 학과를 정하지 않고 뽑는 모집단위 */
	UNDECLARED

}
