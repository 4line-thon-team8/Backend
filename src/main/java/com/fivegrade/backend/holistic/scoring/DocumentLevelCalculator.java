package com.fivegrade.backend.holistic.scoring;

import java.util.Optional;

/**
 * 요소별 수준(1~5)과 전형 프로필의 3축 비중으로 그 전형 기준의 서류 수준 m을 만든다.
 *
 * 학업=학업역량(A), 진로=전공적합성(B), 공동체=공동체역량(C)에 대응한다.
 * 발전가능성(D)은 별도 비중 없이 A·B의 성적 추이 지표에 포함되어 있다.
 * 판단 가능한 축(수준이 null이 아닌 축)이 2개 미만이면 서류 수준을 만들지 않는다.
 * 비중이 없거나 모두 0이면 판단 가능한 축에 균등 비중을 준다.
 */
public final class DocumentLevelCalculator {

	/** 서류 수준 판단에 필요한 최소 축 수 */
	static final int MIN_AXES = 2;

	/** 요소별 수준(1~5). 판단 불가 요소는 null */
	public record ElementLevels(Double academic, Double career, Double community) {
	}

	/** 3축 비중(%). 모르는 축은 null. 전부 null이면 균등으로 본다. */
	public record AxisWeights(Double academic, Double career, Double community) {

		public static AxisWeights equal() {
			return new AxisWeights(null, null, null);
		}
	}

	/** @return 서류 수준 m. 판단 가능한 축이 부족하면 비어 있다. */
	public Optional<Double> compute(ElementLevels levels, AxisWeights weights) {
		Double[] level = {levels.academic(), levels.career(), levels.community()};
		Double[] weight = {weights.academic(), weights.career(), weights.community()};

		int usable = 0;
		for (Double value : level) {
			if (value != null) {
				usable++;
			}
		}
		if (usable < MIN_AXES) {
			return Optional.empty();
		}

		double weightedSum = 0;
		double weightTotal = 0;
		for (int i = 0; i < level.length; i++) {
			if (level[i] == null) {
				continue;
			}
			double w = weight[i] == null ? 0 : Math.max(0, weight[i]);
			weightedSum += w * level[i];
			weightTotal += w;
		}
		if (weightTotal <= 0) {
			double sum = 0;
			for (Double value : level) {
				if (value != null) {
					sum += value;
				}
			}
			return Optional.of(sum / usable);
		}
		return Optional.of(weightedSum / weightTotal);
	}

}
