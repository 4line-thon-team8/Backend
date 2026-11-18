package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticReportInput.ElementView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.IndicatorView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NarrativeAmount;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotObserved;
import com.fivegrade.backend.report.generation.ReportSection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 리포트 입력 자료로 결정론적인 기본 문장을 만든다. AI를 쓰지 않을 때와 AI 결과가 검증을 통과하지 못했을 때의 결과다.
 * 순수 Java 클래스이며, 숫자·합격 전망·대학 이름을 쓰지 않는다.
 *
 * 문장 원칙
 * - 관찰되지 않은 지표와 수준이 낮은 지표는 "없다/부족하다"가 아니라 "근거가 더 필요해 보인다/확인되지 않았다"로 쓴다.
 * - 지표 이름 뒤에는 조사를 붙이지 않는다(받침에 따라 틀려지기 때문). 요소 이름은 모두 받침으로 끝나 조사를 붙일 수 있다.
 */
public final class HolisticReportTemplate {

	static final int MAX_STRENGTHS = 3;
	static final int MAX_TO_DEVELOP = 3;
	static final int MAX_NEXT_STEPS = 4;

	/** 지표별 보완 행동 제안. 일반적인 기록 방향이며 특정 활동이나 전형을 말하지 않는다. */
	private static final Map<String, String> NEXT_STEPS = Map.ofEntries(
			Map.entry("A2", "수업 중 질문하거나 예습·복습을 이어 간 구체적인 사례를 교과 활동 기록에 남겨 보세요."),
			Map.entry("A3", "조사·실험·자료 분석처럼 탐구 방법을 쓴 과정을 단계별로 정리해 보세요."),
			Map.entry("B3", "희망 진로와 관련해 알아보고 경험한 활동을 진로 활동 기록에 구체적으로 남겨 보세요."),
			Map.entry("C1", "모둠 활동에서 의견을 조율하고 역할을 나눈 경험을 구체적으로 기록해 보세요."),
			Map.entry("C2", "친구를 돕거나 배려한 행동을 구체적인 상황과 함께 남겨 보세요."),
			Map.entry("C3", "맡은 일을 끝까지 해낸 경험이나 꾸준히 이어 간 활동을 기록해 보세요."),
			Map.entry("C4", "모둠이나 활동을 이끌고 역할을 조율한 경험이 있다면 구체적으로 남겨 보세요."),
			Map.entry("D1", "스스로 목표를 세우고 실행한 경험을 계획과 결과가 보이게 정리해 보세요."),
			Map.entry("D3", "문제를 찾아 해결 방안이나 아이디어를 제안한 경험을 구체적으로 정리해 보세요."),
			Map.entry("D4", "수업 내용을 더 깊게 파고들거나 다른 분야로 넓혀 본 활동을 기록해 보세요."));

	private static final String GENERAL_NEXT_STEP =
			"지금 잘 드러나는 활동을 과정과 결과가 보이게 구체적으로 계속 기록해 보세요.";

	private static final String CAVEAT =
			"이 분석은 생기부 서술에서 규칙과 모델이 찾아낸 신호를 바탕으로 한 참고용이에요. "
					+ "찾아내지 못한 내용이 있을 수 있어서, 분석에서 확인되지 않았다고 해서 그 역량이 없다는 뜻은 아니에요.";

	private static final String LIMITED_NARRATIVE =
			" 읽어 낸 서술 분량이 많지 않아 해석이 달라질 수 있어요.";

	public HolisticReportContent generate(HolisticReportInput input) {
		List<IndicatorView> observed = input.elements().stream()
				.flatMap(element -> element.indicators().stream())
				.toList();

		List<IndicatorView> strong = observed.stream()
				.filter(indicator -> indicator.band() == ReportBand.HIGH)
				.sorted(Comparator.comparingInt(IndicatorView::level).reversed().thenComparing(IndicatorView::id))
				.limit(MAX_STRENGTHS)
				.toList();

		List<String> developIds = new ArrayList<>();
		List<ReportSection> toDevelop = new ArrayList<>();
		observed.stream()
				.filter(indicator -> indicator.band() == ReportBand.LOW)
				.sorted(Comparator.comparingInt(IndicatorView::level).thenComparing(IndicatorView::id))
				.forEach(indicator -> addDevelop(developIds, toDevelop, indicator.id(),
						indicator.label() + " 관련 내용은 근거가 더 필요해 보여요."));
		input.elements().stream()
				.flatMap(element -> element.notObserved().stream())
				.sorted(Comparator.comparing(NotObserved::id))
				.forEach(indicator -> addDevelop(developIds, toDevelop, indicator.id(),
						indicator.label() + " 관련 내용은 이번 분석에서 뚜렷이 확인되지 않았어요."));

		List<ReportSection> nextSteps = developIds.stream()
				.map(NEXT_STEPS::get)
				.filter(text -> text != null)
				.limit(MAX_NEXT_STEPS)
				.map(ReportSection::template)
				.collect(Collectors.toCollection(ArrayList::new));
		if (nextSteps.isEmpty()) {
			nextSteps.add(ReportSection.template(GENERAL_NEXT_STEP));
		}

		return new HolisticReportContent(
				ReportSection.template(summary(input)),
				strong.stream().map(HolisticReportTemplate::strengthSentence).map(ReportSection::template).toList(),
				toDevelop,
				nextSteps,
				ReportSection.template(caveat(input)));
	}

	private static void addDevelop(List<String> ids, List<ReportSection> sections, String id, String text) {
		if (sections.size() < MAX_TO_DEVELOP) {
			ids.add(id);
			sections.add(ReportSection.template(text));
		}
	}

	private static String strengthSentence(IndicatorView indicator) {
		return indicator.reliability() == HolisticReportParameters.Reliability.LOW
				? indicator.label() + " 관련 내용이 드러나는 것으로 보여요."
				: indicator.label() + " 관련 내용이 비교적 잘 드러나는 편이에요.";
	}

	private static String summary(HolisticReportInput input) {
		List<String> highs = labelsOf(input, ReportBand.HIGH);
		List<String> lows = labelsOf(input, ReportBand.LOW);
		boolean anyElement = input.elements().stream().anyMatch(element -> element.band() != null);
		if (!anyElement) {
			return "이번 분석에서는 뚜렷하게 확인된 평가요소가 적어요.";
		}
		StringBuilder text = new StringBuilder();
		if (!highs.isEmpty()) {
			text.append("생기부 서술에서는 ").append(String.join("·", highs)).append("이 상대적으로 잘 드러나요.");
		}
		if (!lows.isEmpty()) {
			if (text.length() > 0) {
				text.append(' ');
			}
			text.append(String.join("·", lows)).append("은 근거가 더 필요해 보여요.");
		}
		if (text.length() == 0) {
			text.append("생기부 서술에서는 평가요소가 한쪽으로 크게 치우치지 않은 모습이 보여요.");
		}
		return text.toString();
	}

	private static List<String> labelsOf(HolisticReportInput input, ReportBand band) {
		return input.elements().stream()
				.filter(element -> element.band() == band)
				.map(ElementView::label)
				.toList();
	}

	private static String caveat(HolisticReportInput input) {
		return input.narrativeAmount() == NarrativeAmount.LIMITED ? CAVEAT + LIMITED_NARRATIVE : CAVEAT;
	}
}
