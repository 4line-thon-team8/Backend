package com.fivegrade.backend.holistic.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.holistic.report.HolisticNarrative.Detail;
import com.fivegrade.backend.holistic.report.HolisticNarrative.MajorAdvice;
import com.fivegrade.backend.holistic.report.HolisticNarrativeFactsFactory.DesiredMajor;
import com.fivegrade.backend.holistic.report.HolisticNarrativeFactsFactory.EvidenceInput;
import com.fivegrade.backend.holistic.report.HolisticNarrativeValidator.Checked;
import com.fivegrade.backend.holistic.report.HolisticReportInputFactory.IndicatorDef;
import com.fivegrade.backend.holistic.report.HolisticReportInputFactory.TagCount;
import com.fivegrade.backend.holistic.report.ParsedNarrative.EvidenceLine;
import com.fivegrade.backend.holistic.report.ParsedNarrative.MajorLine;
import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 활동 요약·학과별 조언의 사실 자료, 기본 문장, 검증, AI 부분 폴백을 가짜 클라이언트로 확인한다. */
class HolisticNarrativeTest {

	private static final List<IndicatorDef> DEFS = List.of(
			new IndicatorDef("A3", "탐구력", "ACADEMIC"),
			new IndicatorDef("A2", "학업태도", "ACADEMIC"),
			new IndicatorDef("B3", "진로 탐색 활동과 경험", "MAJOR_FIT"),
			new IndicatorDef("C3", "성실성과 규칙준수", "COMMUNITY"));

	private static final Map<String, List<String>> MAJOR_TAGS = Map.of(
			"CSE", List.of("IT·소프트웨어", "데이터·AI"),
			"KOR", List.of("어문·인문학"),
			"NONE", List.of());

	private static HolisticReportInput report(List<TagCount> tags, int chars) {
		return new HolisticReportInputFactory().create(
				DEFS, Set.of("A3", "A2", "B3", "C3"),
				Map.of("A3", 4.4, "C3", 3.8, "B3", 2.0), Map.of("ACADEMIC", 4.4, "MAJOR_FIT", 2.0, "COMMUNITY", 3.8),
				3.4, Map.of("A3", List.of(2)), tags, chars);
	}

	private static HolisticNarrativeFacts facts(List<TagCount> tags, int chars, List<String> evidence,
			DesiredMajor... majors) {
		HolisticReportInput report = report(tags, chars);
		return HolisticNarrativeFactsFactory.create(
				report,
				HolisticStrengthSelector.withDefaults().select(Map.of("A3", 4.4, "C3", 3.8, "B3", 2.0)),
				List.of(majors), MAJOR_TAGS,
				evidence.stream().map(text -> new EvidenceInput(text, 2, "교과세특")).toList());
	}

	private static final List<TagCount> IT_TAGS = List.of(new TagCount("IT·소프트웨어", 5));
	private static final DesiredMajor CSE = new DesiredMajor("CSE", "컴퓨터공학과");
	private static final DesiredMajor KOR = new DesiredMajor("KOR", "국어국문학과");

	@Test
	void 학생_관심_태그와_겹치는_학과_태그를_구분한다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of(), CSE, KOR);

		assertThat(facts.majors().get(0).overlapTags()).containsExactly("IT·소프트웨어");
		assertThat(facts.majors().get(1).overlapTags()).isEmpty();
		assertThat(facts.canSummarize()).isTrue();
	}

	@Test
	void 중복_학과는_한_번만_쓰고_근거_구절은_길이와_개수를_제한한다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of("  코딩 동아리  ", "x".repeat(31), ""), CSE, CSE);

		assertThat(facts.majors()).hasSize(1);
		assertThat(facts.evidenceTexts()).containsExactly("코딩 동아리");
		assertThat(facts.evidence().getFirst().id()).isEqualTo("e1");
	}

	@Test
	void 강점도_관심_태그도_없으면_요약하지_않는다() {
		HolisticNarrativeFacts facts = HolisticNarrativeFactsFactory.create(
				report(List.of(), 5000), List.of(), List.of(CSE), MAJOR_TAGS, List.<EvidenceInput>of());

		assertThat(facts.canSummarize()).isFalse();
		assertThat(new HolisticNarrativeTemplate().write(facts).activitySummary()).isNull();
	}

	@Test
	void 기본_문장은_겹치는_태그_조언을_먼저_쓰고_태그가_없으면_일반_조언이다() {
		HolisticNarrative narrative = new HolisticNarrativeTemplate()
				.write(facts(IT_TAGS, 5000, List.of(), CSE, KOR, new DesiredMajor("NONE", "자유전공")));

		assertThat(narrative.activitySummary().generationMethod()).isEqualTo("TEMPLATE");
		assertThat(narrative.activitySummary().text()).contains("IT·소프트웨어").contains("탐구력");
		assertThat(narrative.activitySummary().text().split("\\.")).hasSize(1);
		assertThat(narrative.majorAdvices()).extracting(MajorAdvice::majorCode).containsExactly("CSE", "KOR", "NONE");
		assertThat(narrative.majorAdvices().get(0).advice().text()).contains("코드");
		assertThat(narrative.majorAdvices().get(2).advice().text()).isEqualTo(HolisticNarrativeTemplate.GENERAL_ADVICE);
	}

	@Test
	void 서술이_적으면_자세히_보기_요약에_해석이_달라질_수_있다고_붙이고_활동_요약은_한_문장으로_둔다() {
		HolisticNarrative narrative = new HolisticNarrativeTemplate().write(facts(IT_TAGS, 100, List.of(), CSE));

		assertThat(narrative.activitySummary().text()).doesNotContain("해석이 달라질 수 있어요");
		assertThat(narrative.detail().overview().text()).contains("해석이 달라질 수 있어요");
	}

	@Test
	void 기본_문장은_자기_검증기를_통과한다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of(), CSE, KOR);
		HolisticNarrative narrative = new HolisticNarrativeTemplate().write(facts);

		ParsedNarrative asParsed = new ParsedNarrative(
				new Line(narrative.activitySummary().text(), List.of("ACADEMIC", "COMMUNITY", "IT·소프트웨어")),
				new Line(narrative.detail().overview().text(), List.of("ACADEMIC", "COMMUNITY", "IT·소프트웨어")),
				List.of(),
				narrative.majorAdvices().stream()
						.map(advice -> new MajorLine(advice.majorCode(),
								new Line(advice.advice().text(), List.of(MAJOR_TAGS.get(advice.majorCode()).getFirst()))))
						.toList());
		Checked checked = HolisticNarrativeValidator.check(facts, asParsed);

		assertThat(checked.rejections()).isEmpty();
	}

	@Test
	void 모든_분야_태그의_기본_조언은_검증_규칙을_지킨다() {
		for (String tag : List.of("IT·소프트웨어", "데이터·AI", "전자·반도체", "기계·제조", "화학·소재", "건축·토목",
				"바이오·생명", "보건·의료", "경영·회계", "경제·금융", "법·행정", "미디어·언론", "심리·교육", "어문·인문학",
				"사회·복지", "디자인·예술", "환경·에너지", "수학·통계", "기초과학", "국제·무역", "농림·식품", "관광·서비스")) {
			String text = HolisticNarrativeTemplate.adviceText(
					new HolisticNarrativeFacts.MajorFacts("X", "가", List.of(tag), List.of()));
			HolisticNarrativeFacts facts = facts(List.of(new TagCount(tag, 1)), 5000, List.of(), CSE);
			ParsedNarrative parsed = new ParsedNarrative(
					new Line("관심 분야는 " + tag + " 쪽으로 읽혀요.", List.of(tag)), OV, List.of(),
					List.of(new MajorLine("CSE", new Line(text, List.of("IT·소프트웨어")))));
			assertThat(HolisticNarrativeValidator.check(facts, parsed).advices())
					.as(tag).containsKey("CSE");
		}
	}

	private ParsedNarrative narrative(String summary, List<String> summaryRefs, String advice, List<String> adviceRefs) {
		return new ParsedNarrative(new Line(summary, summaryRefs), OV, List.of(),
				List.of(new MajorLine("CSE", new Line(advice, adviceRefs))));
	}

	@Test
	void 근거와_문체가_맞는_글은_통과한다() {
		Checked checked = HolisticNarrativeValidator.check(facts(IT_TAGS, 5000, List.of(), CSE),
				narrative("관심 분야는 IT·소프트웨어 쪽이고 탐구력이 잘 드러나요.", List.of("IT·소프트웨어", "A3"),
						"직접 만든 결과물의 과정을 단계별로 남겨 보세요.", List.of("IT·소프트웨어")));

		assertThat(checked.rejections()).isEmpty();
		assertThat(checked.summary()).isNotNull();
		assertThat(checked.advices()).containsKey("CSE");
	}

	@Test
	void 어긋난_글만_버린다() {
		String advice = "직접 만든 결과물의 과정을 단계별로 남겨 보세요.";
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of(), CSE);
		for (String[] bad : new String[][] {
				{"관심이 크다.", "STYLE"},
				{"합격 가능성이 높아요.", "FORBIDDEN"},
				{"3가지 활동이 눈에 띄어요.", "FORBIDDEN"},
				{"성실성과 규칙준수가 돋보여요.", "LABEL_MISMATCH"},
				{"첫째 활동이 보여요.\n둘째 활동이 보여요.", "FORMAT"},
				{"활동이 보여요. 탐구가 보여요.", "STYLE"}}) {
			Checked checked = HolisticNarrativeValidator.check(facts,
					narrative(bad[0], List.of("A3"), advice, List.of("IT·소프트웨어")));
			assertThat(checked.summary()).as(bad[0]).isNull();
			assertThat(checked.rejections()).containsExactly("SUMMARY_" + bad[1]);
			assertThat(checked.advices()).containsKey("CSE");
		}
	}

	@Test
	void 모르는_근거와_모르는_학과_코드는_버린다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of(), CSE);
		ParsedNarrative parsed = new ParsedNarrative(
				new Line("탐구하는 활동이 잘 드러나요.", List.of("Z9")), OV, List.of(),
				List.of(new MajorLine("OTHER", new Line("기록을 남겨 보세요.", List.of("A3"))),
						new MajorLine("CSE", new Line("결과물을 정리해 보세요.", List.of("어문·인문학")))));
		Checked checked = HolisticNarrativeValidator.check(facts, parsed);

		assertThat(checked.rejections()).containsExactly(
				"SUMMARY_UNKNOWN_REF", "ADVICE_UNKNOWN_MAJOR", "ADVICE_UNKNOWN_REF");
	}

	@Test
	void 따옴표_인용은_넘긴_근거_구절에_있을_때만_쓴다() {
		String advice = "「코딩 동아리」 경험을 과정과 함께 남겨 보세요.";
		Checked withEvidence = HolisticNarrativeValidator.check(
				facts(IT_TAGS, 5000, List.of("코딩 동아리 활동"), CSE),
				narrative("탐구하는 활동이 잘 드러나요.", List.of("A3"), advice, List.of("IT·소프트웨어")));
		Checked withoutEvidence = HolisticNarrativeValidator.check(
				facts(IT_TAGS, 5000, List.of(), CSE),
				narrative("탐구하는 활동이 잘 드러나요.", List.of("A3"), advice, List.of("IT·소프트웨어")));

		assertThat(withEvidence.advices()).containsKey("CSE");
		assertThat(withoutEvidence.rejections()).containsExactly("ADVICE_QUOTE");
	}

	@Test
	void 검증을_통과한_글은_LLM_나머지는_TEMPLATE으로_글마다_표시한다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of(), CSE, KOR);
		HolisticNarrativeAiClient client = input -> new ParsedNarrative(
				new Line("합격할 수 있어요.", List.of("A3")), OV, List.of(),
				List.of(new MajorLine("CSE", new Line("직접 만든 결과물을 단계별로 남겨 보세요.", List.of("IT·소프트웨어")))));

		HolisticNarrative narrative = new AiHolisticNarrativeGenerator(client, new HolisticNarrativeTemplate()).write(facts);

		assertThat(narrative.activitySummary().generationMethod()).isEqualTo("TEMPLATE");
		assertThat(narrative.majorAdvices()).extracting(a -> a.advice().generationMethod())
				.containsExactly("LLM", "TEMPLATE");
		assertThat(narrative.majorAdvices()).extracting(MajorAdvice::department).containsExactly("컴퓨터공학과", "국어국문학과");
	}

	@Test
	void 호출이_실패하면_전부_기본_문장이고_예외를_내지_않는다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of(), CSE);
		HolisticNarrativeAiClient client = input -> {
			throw new IllegalStateException("boom");
		};

		HolisticNarrative narrative = new AiHolisticNarrativeGenerator(client, new HolisticNarrativeTemplate()).write(facts);

		assertThat(narrative).isEqualTo(new HolisticNarrativeTemplate().write(facts));
	}

	@Test
	void AI_입력에는_학과_이름과_수준_수치가_없다() {
		Map<String, Object> input = HolisticNarrativePrompt.inputOf(facts(IT_TAGS, 5000, List.of(), CSE));
		String text = input.toString();

		assertThat(text).doesNotContain("컴퓨터공학과");
		assertThat(input).containsKeys("canSummarize", "strengths", "majors", "interestTags");
	}

	// ---- 자세히 보기(detail) ----

	private static final Line OV = new Line("탐구하는 활동이 잘 드러나요.", List.of("A3"));
	private static final Line OVERVIEW_OK = new Line("탐구하는 활동이 2학년 기록에서 잘 드러나요.", List.of("A3"));
	private static final Line SUMMARY_OK = new Line("탐구하는 활동이 잘 드러나요.", List.of("A3"));

	private static ParsedNarrative withEvidence(EvidenceLine... lines) {
		return new ParsedNarrative(SUMMARY_OK, OVERVIEW_OK, List.of(lines), List.of());
	}

	@Test
	void 기본_자세히_보기는_네_영역이_모두_채워지고_다음_단계_마지막은_고정_문장이다() {
		HolisticNarrative narrative = new HolisticNarrativeTemplate().write(facts(IT_TAGS, 5000, List.of(), CSE));
		Detail detail = narrative.detail();

		assertThat(detail.overview().generationMethod()).isEqualTo("TEMPLATE");
		assertThat(detail.strengthEvidence()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
		assertThat(detail.improvements()).hasSizeLessThanOrEqualTo(3);
		assertThat(detail.nextSteps()).hasSizeLessThanOrEqualTo(3);
		assertThat(detail.nextSteps().getLast().text()).isEqualTo(HolisticNarrativeTemplate.FIXED_NEXT_STEP);
		assertThat(detail.strengthEvidence().getFirst().text()).contains("탐구력").contains("2학년");
		assertThat(java.util.stream.Stream.of(detail.strengthEvidence(), detail.improvements(), detail.nextSteps())
				.flatMap(List::stream)).allMatch(text -> "TEMPLATE".equals(text.generationMethod()));
	}

	@Test
	void 요약을_쓸_근거가_없으면_자세히_보기도_없다() {
		HolisticNarrativeFacts facts = HolisticNarrativeFactsFactory.create(
				report(List.of(), 5000), List.of(), List.of(CSE), MAJOR_TAGS, List.<EvidenceInput>of());

		assertThat(new HolisticNarrativeTemplate().write(facts).detail()).isNull();
	}

	@Test
	void 강점_근거는_근거_구절이_있을_때만_받고_학년은_근거의_학년만_허용한다() {
		Line ok = new Line("코딩 동아리 활동에서 탐구력이 2학년 기록에 드러나요.", List.of("A3"));
		EvidenceLine good = new EvidenceLine(ok, List.of("e1"));

		HolisticNarrativeFacts withEvidence = facts(IT_TAGS, 5000, List.of("코딩 동아리 활동"), CSE);
		assertThat(HolisticNarrativeValidator.check(withEvidence, withEvidence(good)).strengthEvidence())
				.containsExactly(ok);

		// 근거 구절을 보내지 않았으면 강점 근거는 받지 않는다
		HolisticNarrativeFacts without = facts(IT_TAGS, 5000, List.of(), CSE);
		assertThat(HolisticNarrativeValidator.check(without, withEvidence(good)).strengthEvidence()).isEmpty();
	}

	@Test
	void 어긋난_강점_근거는_사유별로_버린다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of("코딩 동아리 활동"), CSE);
		Map<String, EvidenceLine> bad = new java.util.LinkedHashMap<>();
		bad.put("EVIDENCE_UNKNOWN_EVIDENCE_ID", new EvidenceLine(new Line("탐구력이 드러나요.", List.of("A3")), List.of("e9")));
		bad.put("EVIDENCE_NO_EVIDENCE_IDS", new EvidenceLine(new Line("탐구력이 드러나요.", List.of("A3")), List.of()));
		bad.put("EVIDENCE_REF_NOT_STRENGTH", new EvidenceLine(new Line("탐구력이 드러나요.", List.of("B3")), List.of("e1")));
		bad.put("EVIDENCE_GRADE", new EvidenceLine(new Line("탐구력이 3학년 기록에 드러나요.", List.of("A3")), List.of("e1")));
		bad.put("EVIDENCE_FORBIDDEN", new EvidenceLine(new Line("탐구력이 3가지로 드러나요.", List.of("A3")), List.of("e1")));
		bad.put("EVIDENCE_QUOTE", new EvidenceLine(new Line("「다른 활동」에서 탐구력이 드러나요.", List.of("A3")), List.of("e1")));
		for (var entry : bad.entrySet()) {
			Checked checked = HolisticNarrativeValidator.check(facts, withEvidence(entry.getValue()));
			assertThat(checked.strengthEvidence()).as(entry.getKey()).isEmpty();
			assertThat(checked.rejections()).as(entry.getKey()).contains(entry.getKey());
		}
	}

	@Test
	void 학년_표현_말고_다른_숫자는_계속_막는다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of(), CSE);
		for (String text : List.of("탐구하는 활동이 13학년에 드러나요.", "탐구하는 활동이 2번 드러나요.", "탐구하는 활동이 2학년 3건 드러나요.")) {
			Checked checked = HolisticNarrativeValidator.check(facts, new ParsedNarrative(
					new Line(text, List.of("A3")), OV, List.of(), List.of()));
			assertThat(checked.summary()).as(text).isNull();
		}
	}

	@Test
	void 요약은_한_문장만_통과한다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of(), CSE);
		Checked checked = HolisticNarrativeValidator.check(facts, new ParsedNarrative(
				new Line("탐구하는 활동이 보여요. 협력한 경험도 보여요.", List.of("A3")), OVERVIEW_OK, List.of(), List.of()));

		assertThat(checked.summary()).isNull();
		assertThat(checked.overview()).isEqualTo(OVERVIEW_OK);
	}

	@Test
	void AI가_통과한_자세히_보기만_LLM으로_표시하고_보완점과_다음_단계는_항상_TEMPLATE이다() {
		HolisticNarrativeFacts facts = facts(IT_TAGS, 5000, List.of("코딩 동아리 활동"), CSE);
		Line evidence = new Line("코딩 동아리 활동에서 탐구력이 2학년 기록에 드러나요.", List.of("A3"));
		HolisticNarrativeAiClient client = input -> new ParsedNarrative(SUMMARY_OK, OVERVIEW_OK,
				List.of(new EvidenceLine(evidence, List.of("e1")),
						new EvidenceLine(new Line("합격에 유리해요.", List.of("A3")), List.of("e1"))),
				List.of());

		Detail detail = new AiHolisticNarrativeGenerator(client, new HolisticNarrativeTemplate()).write(facts).detail();
		Detail template = new HolisticNarrativeTemplate().write(facts).detail();

		assertThat(detail.overview().generationMethod()).isEqualTo("LLM");
		assertThat(detail.strengthEvidence()).extracting(t -> t.generationMethod()).containsExactly("LLM");
		assertThat(detail.improvements()).isEqualTo(template.improvements());
		assertThat(detail.nextSteps()).isEqualTo(template.nextSteps());
	}

	@Test
	void AI_입력에는_근거_구절_번호와_구절이_들어가고_학과_이름은_없다() {
		Map<String, Object> input = HolisticNarrativePrompt.inputOf(facts(IT_TAGS, 5000, List.of("코딩 동아리 활동"), CSE));

		assertThat(input.toString()).contains("e1").contains("코딩 동아리 활동").doesNotContain("컴퓨터공학과");
	}
}
