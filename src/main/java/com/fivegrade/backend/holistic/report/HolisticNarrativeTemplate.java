package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticNarrative.Detail;
import com.fivegrade.backend.holistic.report.HolisticNarrative.MajorAdvice;
import com.fivegrade.backend.holistic.report.HolisticNarrative.Text;
import com.fivegrade.backend.holistic.report.HolisticNarrativeFacts.MajorFacts;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NarrativeAmount;
import com.fivegrade.backend.holistic.report.HolisticStrengthSelector.Strength;
import com.fivegrade.backend.report.generation.ReportSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 사실 자료로 결정론적인 기본 문장을 만든다. AI를 쓰지 않을 때와 AI 결과가 검증을 통과하지 못했을 때의 결과다.
 * 순수 Java 클래스이며, 숫자·합격 전망·대학 이름을 쓰지 않는다.
 *
 * 문장 원칙
 * - 지표 이름·태그 이름 뒤에는 조사를 붙이지 않는다(받침에 따라 틀려지기 때문). 받침과 상관없는 "에", "쪽으로"만 쓴다.
 * - 학과별 조언은 학과 태그(관심 태그와 겹치면 그 태그 먼저)를 기준으로 한 일반적인 기록 방향이다.
 *   특정 활동이나 전형, 합격 가능성을 말하지 않는다.
 */
public final class HolisticNarrativeTemplate implements HolisticNarrativeWriter {

	static final int MAX_LABELS = 3;
	static final int MAX_TAGS = 2;

	/** 전공 태그별 조언. 키는 tags.csv 의 분야 태그다. */
	private static final Map<String, String> TAG_ADVICE = Map.ofEntries(
			Map.entry("IT·소프트웨어", "직접 만들어 보거나 문제를 코드로 풀어 본 경험을 과정과 결과가 보이게 남겨 보세요."),
			Map.entry("데이터·AI", "자료를 모아 분석하고 해석한 경험을 사용한 방법과 함께 정리해 보세요."),
			Map.entry("전자·반도체", "회로나 장치의 원리를 직접 알아보고 실험해 본 과정을 구체적으로 남겨 보세요."),
			Map.entry("기계·제조", "물건이 작동하는 원리를 분석하거나 직접 만들어 본 경험을 단계별로 기록해 보세요."),
			Map.entry("화학·소재", "물질의 성질을 실험으로 확인하고 결과를 해석한 경험을 구체적으로 남겨 보세요."),
			Map.entry("건축·토목", "공간이나 구조물을 관찰하고 설계해 본 경험을 이유와 함께 정리해 보세요."),
			Map.entry("바이오·생명", "생명 현상을 관찰하고 가설을 세워 확인한 탐구 과정을 구체적으로 남겨 보세요."),
			Map.entry("보건·의료", "건강과 관련한 주제를 조사하고 사람을 배려한 경험을 구체적으로 기록해 보세요."),
			Map.entry("경영·회계", "조직이나 수치를 다루며 계획을 세우고 실행한 경험을 결과와 함께 남겨 보세요."),
			Map.entry("경제·금융", "경제 현상을 자료로 살펴보고 자신의 생각을 정리해 본 경험을 남겨 보세요."),
			Map.entry("법·행정", "사회 규칙이나 제도를 조사하고 토론한 경험을 근거와 함께 정리해 보세요."),
			Map.entry("미디어·언론", "정보를 모아 글이나 영상으로 전달해 본 경험을 과정과 함께 기록해 보세요."),
			Map.entry("심리·교육", "사람의 생각과 행동을 이해하려 한 활동이나 가르쳐 본 경험을 구체적으로 남겨 보세요."),
			Map.entry("어문·인문학", "글이나 작품을 깊이 읽고 자신의 해석을 정리한 경험을 구체적으로 남겨 보세요."),
			Map.entry("사회·복지", "사회 문제를 조사하고 도움이 되는 방법을 직접 실천한 경험을 기록해 보세요."),
			Map.entry("디자인·예술", "아이디어를 시각이나 작품으로 표현한 과정을 시안과 함께 정리해 보세요."),
			Map.entry("환경·에너지", "환경 문제를 조사하고 줄이는 방법을 직접 실천한 경험을 구체적으로 남겨 보세요."),
			Map.entry("수학·통계", "문제를 풀며 방법을 비교하고 일반화해 본 경험을 풀이 과정과 함께 남겨 보세요."),
			Map.entry("기초과학", "원리를 탐구하며 실험이나 자료로 확인한 경험을 단계별로 정리해 보세요."),
			Map.entry("국제·무역", "다른 나라의 문화나 경제를 조사하고 소통해 본 경험을 구체적으로 남겨 보세요."),
			Map.entry("농림·식품", "식품이나 자연 자원을 관찰하고 실험해 본 경험을 과정과 함께 정리해 보세요."),
			Map.entry("관광·서비스", "사람을 응대하거나 기획해 본 경험에서 배운 점을 구체적으로 기록해 보세요."));

	static final String GENERAL_ADVICE =
			"희망하는 분야와 이어지는 활동을 과정과 결과가 보이게 구체적으로 기록해 보세요.";

	private static final String LIMITED_NARRATIVE =
			"읽어 낸 서술 분량이 많지 않아 해석이 달라질 수 있어요.";

	/** 다음 단계의 마지막에 항상 붙는 서버 고정 문장 */
	static final String FIXED_NEXT_STEP = "지원하려는 모집요강에서 평가요소 반영 비율을 확인해 보세요.";

	static final int MAX_DETAIL_ITEMS = 3;

	@Override
	public HolisticNarrative write(HolisticNarrativeFacts facts) {
		Text summary = summary(facts);
		return new HolisticNarrative(summary, facts.majors().stream()
				.map(major -> new MajorAdvice(major.code(), major.department(), Text.template(adviceText(major))))
				.toList(), detail(facts, summary));
	}

	/** 자세히 보기. 활동 요약을 쓸 근거가 없으면 만들지 않는다. */
	private static Detail detail(HolisticNarrativeFacts facts, Text summary) {
		if (summary == null) {
			return null;
		}
		HolisticReportContent report = new HolisticReportTemplate().generate(facts.report());
		List<Text> improvements = report.toDevelop().stream()
				.limit(MAX_DETAIL_ITEMS).map(HolisticNarrativeTemplate::fromReport).toList();
		List<Text> nextSteps = new ArrayList<>(report.nextSteps().stream()
				.limit(MAX_DETAIL_ITEMS - 1).map(HolisticNarrativeTemplate::fromReport).toList());
		nextSteps.add(Text.template(FIXED_NEXT_STEP));
		return new Detail(overview(facts, summary), strengthEvidence(facts), improvements, nextSteps);
	}

	private static Text fromReport(ReportSection section) {
		return Text.template(section.text());
	}

	/** 자세히 보기의 전체 요약. 활동 요약 문장에 두 번째 문장(분량이 적으면 그 안내, 아니면 확인된 학년)을 붙인다. */
	static Text overview(HolisticNarrativeFacts facts, Text summary) {
		String second = null;
		if (facts.report().narrativeAmount() == NarrativeAmount.LIMITED) {
			second = LIMITED_NARRATIVE;
		} else {
			TreeSet<Integer> years = new TreeSet<>();
			facts.report().elements().forEach(element ->
					element.indicators().forEach(indicator -> years.addAll(indicator.observedYears())));
			if (!years.isEmpty()) {
				second = "활동은 " + gradesText(years) + "학년 기록에서 확인돼요.";
			}
		}
		return Text.template(second == null ? summary.text() : summary.text() + " " + second);
	}

	/** 강점마다 확인된 학년을 알려 주는 기본 문장. 활동 내용은 쓰지 않는다. */
	static List<Text> strengthEvidence(HolisticNarrativeFacts facts) {
		return facts.strengths().stream()
				.limit(MAX_DETAIL_ITEMS)
				.map(strength -> {
					TreeSet<Integer> years = new TreeSet<>();
					facts.report().elements().forEach(element -> element.indicators().stream()
							.filter(indicator -> indicator.id().equals(strength.indicatorId()))
							.forEach(indicator -> years.addAll(indicator.observedYears())));
					return Text.template(years.isEmpty()
							? strength.label() + " 관련 내용이 기록에서 확인돼요."
							: strength.label() + " 관련 내용이 " + gradesText(years) + "학년 기록에서 확인돼요.");
				})
				.toList();
	}

	private static String gradesText(java.util.Collection<Integer> years) {
		return years.stream().map(String::valueOf).collect(Collectors.joining("·"));
	}

	/** 학과 하나의 기본 조언 문장. 검증을 통과하지 못한 AI 조언을 대신할 때도 쓴다. */
	static String adviceText(MajorFacts major) {
		List<String> ordered = new ArrayList<>(major.overlapTags());
		ordered.addAll(major.tags());
		return ordered.stream()
				.map(TAG_ADVICE::get)
				.filter(text -> text != null)
				.findFirst()
				.orElse(GENERAL_ADVICE);
	}

	/** 활동 요약 한 문장. 화면에는 첫 문장만 나오므로 관심 분야와 강점을 한 문장에 담는다. */
	private static Text summary(HolisticNarrativeFacts facts) {
		if (!facts.canSummarize()) {
			return null;
		}
		List<String> tags = facts.report().interestTags().stream().limit(MAX_TAGS).toList();
		List<String> labels = facts.strengths().stream().map(Strength::label).limit(MAX_LABELS).toList();
		String tagText = String.join(", ", tags);
		String labelText = String.join(", ", labels);
		if (!tags.isEmpty() && !labels.isEmpty()) {
			return Text.template(tagText + " 쪽 관심과 함께 " + labelText + "에 해당하는 활동이 눈에 띄어요.");
		}
		if (!tags.isEmpty()) {
			return Text.template("관심 분야는 " + tagText + " 쪽으로 읽혀요.");
		}
		return Text.template(labelText + "에 해당하는 활동이 눈에 띄어요.");
	}
}
