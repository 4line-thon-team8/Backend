package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticReportInput.ElementView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.IndicatorView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NarrativeAmount;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotMeasured;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotObserved;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 서술 분석 평가 결과를 리포트 입력 자료로 바꾼다. Spring과 DB에 의존하지 않는 순수 Java 클래스다.
 *
 * 규칙이 있는 지표(observable)에서 신호가 잡히면 관찰된 지표, 잡히지 않으면 "이번에는 관찰되지 않음"이다.
 * 규칙이 없는 지표는 측정하지 않은 것이라 따로 알린다. 관찰되지 않은 것과 측정하지 않은 것은 "없다"가 아니다.
 */
public final class HolisticReportInputFactory {

	/** 평가요소 표시 순서와 이름 */
	static final Map<String, String> ELEMENT_LABELS = new LinkedHashMap<>();

	static {
		ELEMENT_LABELS.put("ACADEMIC", "학업역량");
		ELEMENT_LABELS.put("MAJOR_FIT", "전공적합성");
		ELEMENT_LABELS.put("COMMUNITY", "공동체역량");
		ELEMENT_LABELS.put("POTENTIAL", "발전가능성");
	}

	static final int MAX_TAGS = 5;

	/** 규칙 사전의 지표 */
	public record IndicatorDef(String id, String label, String element) {
	}

	public record TagCount(String tag, int count) {
	}

	private final HolisticReportParameters parameters;

	public HolisticReportInputFactory() {
		this(HolisticReportParameters.DEFAULT);
	}

	public HolisticReportInputFactory(HolisticReportParameters parameters) {
		this.parameters = parameters;
	}

	/**
	 * @param indicators      규칙 사전의 모든 지표(사전 순서 유지)
	 * @param observableIds   규칙이 하나라도 있는 지표 ID
	 * @param indicatorLevels 신호가 관찰된 지표의 수준(1~5)
	 * @param elementLevels   요소별 수준(1~5). 판단하지 못한 요소는 없다.
	 * @param overallLevel    전체 서류 수준(1~5)
	 * @param observedYears   지표 ID → 신호가 나타난 학년
	 * @param tags            전공 키워드 태그와 빈도
	 * @param narrativeChars  읽은 서술의 전체 글자 수
	 */
	public HolisticReportInput create(
			List<IndicatorDef> indicators,
			Set<String> observableIds,
			Map<String, Double> indicatorLevels,
			Map<String, Double> elementLevels,
			double overallLevel,
			Map<String, List<Integer>> observedYears,
			List<TagCount> tags,
			long narrativeChars
	) {
		List<ElementView> elements = new ArrayList<>();
		for (Map.Entry<String, String> element : ELEMENT_LABELS.entrySet()) {
			elements.add(elementView(element.getKey(), element.getValue(), indicators, observableIds,
					indicatorLevels, elementLevels, observedYears));
		}

		List<NotMeasured> notMeasured = indicators.stream()
				.filter(indicator -> !observableIds.contains(indicator.id()))
				.map(indicator -> new NotMeasured(indicator.id(), indicator.label()))
				.toList();

		NarrativeAmount amount = narrativeChars < parameters.limitedBelowChars()
				? NarrativeAmount.LIMITED : NarrativeAmount.SUFFICIENT;

		return new HolisticReportInput(amount, parameters.bandOf(overallLevel), elements, notMeasured, topTags(tags));
	}

	private ElementView elementView(
			String elementId,
			String label,
			List<IndicatorDef> indicators,
			Set<String> observableIds,
			Map<String, Double> indicatorLevels,
			Map<String, Double> elementLevels,
			Map<String, List<Integer>> observedYears
	) {
		List<IndicatorView> observed = new ArrayList<>();
		List<NotObserved> notObserved = new ArrayList<>();
		for (IndicatorDef indicator : indicators) {
			if (!indicator.element().equals(elementId) || !observableIds.contains(indicator.id())) {
				continue;
			}
			Double level = indicatorLevels.get(indicator.id());
			HolisticReportParameters.Reliability reliability = parameters.reliabilityOf(indicator.id());
			if (level == null) {
				notObserved.add(new NotObserved(indicator.id(), indicator.label(), reliability));
			} else {
				observed.add(new IndicatorView(indicator.id(), indicator.label(), parameters.bandOf(level),
						parameters.displayLevel(level), years(observedYears.get(indicator.id())), reliability));
			}
		}
		Double elementLevel = elementLevels.get(elementId);
		return new ElementView(
				elementId,
				label,
				elementLevel == null ? null : parameters.bandOf(elementLevel),
				elementLevel == null ? null : parameters.displayLevel(elementLevel),
				observed,
				notObserved);
	}

	private static List<Integer> years(List<Integer> years) {
		return years == null ? List.of() : List.copyOf(new TreeSet<>(years));
	}

	private static List<String> topTags(List<TagCount> tags) {
		return tags.stream()
				.filter(tag -> tag.count() > 0)
				.sorted(Comparator.comparingInt(TagCount::count).reversed().thenComparing(TagCount::tag))
				.limit(MAX_TAGS)
				.map(TagCount::tag)
				.toList();
	}
}
