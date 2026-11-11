package com.fivegrade.backend.recommendation.dto;

import java.util.HashSet;
import java.util.List;

/**
 * 추천 서비스의 출력. 추천 카드 목록과 안내(warnings)를 담는다.
 *
 * 카드는 0~6장이다. 기능 명세는 "항상 정확히 6장"이라고 쓰지만, 후보를 넓혀도 6장을 못 채우는 경우의
 * 응답(빈 목록과 안내 문구)은 FE·기획 확인 전이라 여기서는 있는 만큼만 담고 warnings 로 알린다.
 * 카드는 지망 순서(rank 1부터 빠짐없이)대로 정렬되어 있고, 같은 대학·학과·전형은 겹치지 않는다.
 * 지원 대학은 요청에 지정하지 않으므로 이 결과에는 사용자가 고른 대학 정보가 없다.
 *
 * @param cards    추천 원서
 * @param warnings 가정·자료 부족 안내. 없으면 빈 목록
 */
public record RecommendationResult(
		List<RecommendedCard> cards,
		List<RecommendationWarning> warnings
) {

	/** 수시 지원 한도 기준 최대 추천 장수 */
	public static final int MAX_CARDS = 6;

	public RecommendationResult {
		if (cards == null || warnings == null) {
			throw new IllegalArgumentException("cards and warnings must not be null");
		}
		if (cards.size() > MAX_CARDS) {
			throw new IllegalArgumentException("cards must not exceed 6");
		}
		HashSet<String> targets = new HashSet<>();
		for (int i = 0; i < cards.size(); i++) {
			RecommendedCard card = cards.get(i);
			if (card == null) {
				throw new IllegalArgumentException("cards must not contain null");
			}
			if (card.rank() != i + 1) {
				throw new IllegalArgumentException("cards must be ordered by rank starting from 1");
			}
			if (!targets.add(card.targetKey())) {
				throw new IllegalArgumentException("cards must not contain the same target twice");
			}
		}
		for (RecommendationWarning warning : warnings) {
			if (warning == null) {
				throw new IllegalArgumentException("warnings must not contain null");
			}
			if (warning.rank() != null && warning.rank() > cards.size()) {
				throw new IllegalArgumentException("warning rank must refer to an existing card");
			}
		}
		cards = List.copyOf(cards);
		warnings = List.copyOf(warnings);
	}

	/** 추천할 후보가 하나도 없을 때의 결과 */
	public static RecommendationResult empty() {
		return new RecommendationResult(
				List.of(),
				List.of(RecommendationWarning.forResult(WarningCode.NO_CANDIDATES))
		);
	}

}
