package com.fivegrade.backend.holistic.service;

import com.fivegrade.backend.holistic.scoring.DocumentContext;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator;
import java.util.Map;

/**
 * 서술 분석 요청을 평가한 결과. 서류를 반영했거나(Applied), 반영하지 않았고 그 이유를 안다(Skipped).
 * 반영하지 못한 것은 오류가 아니다. 성적 기준 결과는 그대로 돌려준다.
 */
public sealed interface HolisticEvaluation {

	enum SkipReason {
		/** 서술 분석을 보내지 않았다. */
		NOT_PROVIDED,
		/** 서버에서 서술 반영 기능을 꺼 두었다. */
		DISABLED,
		/** 서버가 받아 주는 규칙 버전이 아니다. */
		RULES_OUTDATED,
		/** 읽은 서술이 너무 적다. */
		TOO_SHORT,
		/** 서류 수준을 판단할 만큼 요소 수준을 만들지 못했다. */
		INSUFFICIENT_SIGNALS
	}

	/**
	 * @param rulesVersion    분석에 쓴 규칙 버전
	 * @param indicatorLevels 관찰한 지표의 수준(1~5)
	 * @param elementLevels   요소별 수준(1~5). 판단 못 한 요소는 들어 있지 않다.
	 * @param context         추천에 넘기는 서류 입력
	 */
	record Applied(
			String rulesVersion,
			Map<String, Double> indicatorLevels,
			Map<String, Double> elementLevels,
			DocumentContext context
	) implements HolisticEvaluation {

		public Applied {
			indicatorLevels = Map.copyOf(indicatorLevels);
			elementLevels = Map.copyOf(elementLevels);
		}

		public double overallLevel() {
			return context.overallLevel();
		}

		public DocumentLevelCalculator.ElementLevels axisLevels() {
			return context.levels();
		}
	}

	record Skipped(SkipReason reason) implements HolisticEvaluation {
	}

}
