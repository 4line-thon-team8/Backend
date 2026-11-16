package com.fivegrade.backend.holistic.scoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 지표 신호로 지표 수준을 만들고, 같은 평가요소의 지표들을 평균해 요소 수준(1~5)을 만든다.
 *
 * 규칙 사전에 규칙이 있는 지표만 "관찰할 수 있는 지표"로 본다. 규칙이 없는 지표는 신호가 없는 것이
 * 아니라 측정하지 못한 것이므로 평균에 넣지 않는다. 관찰할 수 있는 지표가 없는 요소는 수준이 null 이다.
 * 관찰할 수 있는 지표인데 신호가 오지 않았다면 근거가 없는 것으로 보고 가장 낮은 수준을 준다.
 *
 * Spring과 DB에 의존하지 않는 순수 Java 클래스다.
 */
public final class ElementLevelCalculator {

	public static final String ACADEMIC = "ACADEMIC";
	public static final String MAJOR_FIT = "MAJOR_FIT";
	public static final String COMMUNITY = "COMMUNITY";
	public static final String POTENTIAL = "POTENTIAL";

	/** 요청에서 온 지표 신호 한 건 */
	public record Observation(String indicatorId, int count, double quality) {
	}

	/**
	 * @param indicatorLevels 관찰할 수 있는 지표의 수준(1~5)
	 * @param elementLevels   요소별 수준(1~5). 관찰할 수 있는 지표가 없는 요소는 들어 있지 않다.
	 */
	public record Result(Map<String, Double> indicatorLevels, Map<String, Double> elementLevels) {

		public Result {
			indicatorLevels = Map.copyOf(indicatorLevels);
			elementLevels = Map.copyOf(elementLevels);
		}

		/** 서류 수준 계산에 넘기는 3축 수준. 발전가능성은 별도 비중이 없어 포함하지 않는다. */
		public DocumentLevelCalculator.ElementLevels axisLevels() {
			return new DocumentLevelCalculator.ElementLevels(
					elementLevels.get(ACADEMIC), elementLevels.get(MAJOR_FIT), elementLevels.get(COMMUNITY));
		}

		/** 발전가능성 수준. 판단 불가면 null */
		public Double potential() {
			return elementLevels.get(POTENTIAL);
		}
	}

	private final IndicatorLevelParameters parameters;

	public ElementLevelCalculator() {
		this(IndicatorLevelParameters.DEFAULT);
	}

	public ElementLevelCalculator(IndicatorLevelParameters parameters) {
		this.parameters = parameters;
	}

	/**
	 * @param observableIndicators 규칙이 있는 지표 ID → 그 지표의 평가요소
	 * @param observations         요청의 지표 신호. 규칙이 없는 지표의 신호는 무시한다.
	 */
	public Result compute(Map<String, String> observableIndicators, List<Observation> observations) {
		Map<String, Observation> byId = new HashMap<>();
		for (Observation observation : observations) {
			byId.put(observation.indicatorId(), observation);
		}

		Map<String, Double> indicatorLevels = new HashMap<>();
		Map<String, double[]> elementSums = new HashMap<>();
		for (Map.Entry<String, String> entry : observableIndicators.entrySet()) {
			Observation observation = byId.get(entry.getKey());
			double level = observation == null
					? parameters.noEvidenceLevel()
					: parameters.level(observation.count(), observation.quality());
			indicatorLevels.put(entry.getKey(), level);
			double[] sum = elementSums.computeIfAbsent(entry.getValue(), key -> new double[2]);
			sum[0] += level;
			sum[1] += 1;
		}

		Map<String, Double> elementLevels = new HashMap<>();
		elementSums.forEach((element, sum) -> elementLevels.put(element, sum[0] / sum[1]));
		return new Result(indicatorLevels, elementLevels);
	}

}
