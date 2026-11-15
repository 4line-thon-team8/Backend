package com.fivegrade.backend.recommendation.dto;

/**
 * 추천 결과의 가정·자료 부족 안내 한 건.
 *
 * @param code 안내 종류
 * @param rank 해당 카드의 지망 순서. 결과 전체에 해당하면 null
 */
public record RecommendationWarning(WarningCode code, Integer rank) {

	public RecommendationWarning {
		if (code == null) {
			throw new IllegalArgumentException("code must not be null");
		}
		if (rank != null && (rank < 1 || rank > RecommendationResult.MAX_CARDS)) {
			throw new IllegalArgumentException("rank must be between 1 and 6");
		}
	}

	public static RecommendationWarning forResult(WarningCode code) {
		return new RecommendationWarning(code, null);
	}

	public static RecommendationWarning forCard(WarningCode code, int rank) {
		return new RecommendationWarning(code, rank);
	}

}
