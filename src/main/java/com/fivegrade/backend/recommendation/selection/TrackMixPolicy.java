package com.fivegrade.backend.recommendation.selection;

import java.util.List;

/**
 * 카드를 고를 때 전형 구성(학종 장수, 안전판 등)에 거는 제한. {@link CardSelector}가 후보를 담기 전에 묻는다.
 *
 * 구현은 Spring과 DB에 의존하지 않는 순수 Java여야 한다. 제한을 지키면 6장을 못 채우는 경우
 * 선택기가 마지막 단계에서 제한을 풀고 채운다. 이때 푼 카드는 {@link CardSelector.Outcome#relaxedUnitIds()}로 알린다.
 */
public interface TrackMixPolicy {

	/**
	 * @param picked 지금까지 고른 카드
	 * @param next   담으려는 후보
	 * @return 담아도 되면 true
	 */
	boolean allows(List<ScoredCandidate> picked, ScoredCandidate next);

}
