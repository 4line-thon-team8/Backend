package com.fivegrade.backend.holistic.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.holistic.report.HolisticReportInputFactory.IndicatorDef;
import com.fivegrade.backend.report.generation.ReportSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 템플릿 리포트 문장의 내용과 표현 원칙을 확인한다. DB나 Spring 없이 실행한다. */
class HolisticReportTemplateTest {

	private static final List<IndicatorDef> DEFS = List.of(
			new IndicatorDef("A2", "학업태도", "ACADEMIC"),
			new IndicatorDef("A3", "탐구력", "ACADEMIC"),
			new IndicatorDef("B3", "진로 탐색 활동과 경험", "MAJOR_FIT"),
			new IndicatorDef("C1", "협업과 소통능력", "COMMUNITY"),
			new IndicatorDef("D1", "자기주도성", "POTENTIAL"));

	private static final Set<String> OBSERVABLE = Set.of("A2", "A3", "B3", "C1", "D1");

	private final HolisticReportInputFactory factory = new HolisticReportInputFactory();
	private final HolisticReportTemplate template = new HolisticReportTemplate();

	private HolisticReportContent content(Map<String, Double> indicators, Map<String, Double> elements, long chars) {
		HolisticReportInput input = factory.create(DEFS, OBSERVABLE, indicators, elements, 3.0, Map.of(), List.of(), chars);
		return template.generate(input);
	}

	private static List<String> allTexts(HolisticReportContent content) {
		List<ReportSection> all = new ArrayList<>();
		all.add(content.summary());
		all.addAll(content.strengths());
		all.addAll(content.toDevelop());
		all.addAll(content.nextSteps());
		all.add(content.caveat());
		return all.stream().map(ReportSection::text).toList();
	}

	@Test
	void 구간이_높은_지표는_강점으로_낮거나_관찰되지_않은_지표는_보완점으로_쓴다() {
		HolisticReportContent content = content(
				Map.of("A3", 4.4, "B3", 2.0),
				Map.of("ACADEMIC", 4.4, "MAJOR_FIT", 2.0), 5000);

		assertThat(content.strengths()).hasSize(1);
		assertThat(content.strengths().get(0).text()).contains("탐구력");
		assertThat(content.toDevelop()).extracting(ReportSection::text)
				.anyMatch(text -> text.contains("진로 탐색 활동과 경험") && text.contains("근거가 더 필요"))
				.anyMatch(text -> text.contains("학업태도") && text.contains("확인되지 않았"));
	}

	@Test
	void 관찰되지_않은_지표를_부족하거나_없다고_단정하지_않는다() {
		HolisticReportContent content = content(Map.of("A3", 4.4), Map.of("ACADEMIC", 4.4), 5000);

		for (String text : allTexts(content)) {
			assertThat(text).doesNotContain("부족해요").doesNotContain("없어요").doesNotContain("못해요")
					.doesNotContain("미흡");
		}
	}

	@Test
	void 보완점마다_행동_제안이_연결된다() {
		HolisticReportContent content = content(Map.of("B3", 2.0), Map.of("MAJOR_FIT", 2.0), 5000);

		assertThat(content.nextSteps()).extracting(ReportSection::text)
				.anyMatch(text -> text.contains("희망 진로"));
	}

	@Test
	void 보완점이_없으면_일반_제안_하나를_준다() {
		HolisticReportInput input = factory.create(
				List.of(new IndicatorDef("A3", "탐구력", "ACADEMIC")), Set.of("A3"),
				Map.of("A3", 4.4), Map.of("ACADEMIC", 4.4), 4.4, Map.of(), List.of(), 5000);

		HolisticReportContent content = template.generate(input);

		assertThat(content.toDevelop()).isEmpty();
		assertThat(content.nextSteps()).hasSize(1);
		assertThat(content.nextSteps().get(0).text()).contains("계속 기록");
	}

	@Test
	void 요약은_높은_요소와_낮은_요소를_말한다() {
		HolisticReportContent content = content(
				Map.of("A3", 4.4, "B3", 2.0),
				Map.of("ACADEMIC", 4.4, "MAJOR_FIT", 2.0), 5000);

		assertThat(content.summary().text())
				.contains("학업역량이 상대적으로 잘 드러나요")
				.contains("전공적합성은 근거가 더 필요해 보여요");
	}

	@Test
	void 모든_문장은_숫자와_합격_전망_표현이_없다() {
		HolisticReportContent content = content(
				Map.of("A3", 4.4, "B3", 2.0, "C1", 3.0),
				Map.of("ACADEMIC", 4.4, "MAJOR_FIT", 2.0, "COMMUNITY", 3.0), 2000);

		for (String text : allTexts(content)) {
			assertThat(text).doesNotContainPattern("[0-9]");
			assertThat(text).doesNotContain("합격").doesNotContain("확률").doesNotContain("점수").doesNotContain("반드시");
		}
	}

	@Test
	void 서술_분량이_적으면_주의_문구에_덧붙인다() {
		HolisticReportContent limited = content(Map.of("A3", 4.4), Map.of("ACADEMIC", 4.4), 1000);
		HolisticReportContent enough = content(Map.of("A3", 4.4), Map.of("ACADEMIC", 4.4), 5000);

		assertThat(limited.caveat().text()).contains("서술 분량이 많지 않아");
		assertThat(enough.caveat().text()).doesNotContain("서술 분량이 많지 않아");
		assertThat(enough.caveat().text()).contains("그 역량이 없다는 뜻은 아니에요");
	}

	@Test
	void 문장은_모두_템플릿으로_표시한다() {
		HolisticReportContent content = content(Map.of("A3", 4.4), Map.of("ACADEMIC", 4.4), 5000);

		assertThat(content.summary().generationMethod()).isEqualTo("template");
		assertThat(content.caveat().generationMethod()).isEqualTo("template");
	}

	@Test
	void 강점_보완점_제안은_개수_상한을_지킨다() {
		List<IndicatorDef> defs = List.of(
				new IndicatorDef("A2", "학업태도", "ACADEMIC"), new IndicatorDef("A3", "탐구력", "ACADEMIC"),
				new IndicatorDef("B3", "진로 탐색 활동과 경험", "MAJOR_FIT"), new IndicatorDef("C1", "협업과 소통능력", "COMMUNITY"),
				new IndicatorDef("C2", "나눔과 배려", "COMMUNITY"), new IndicatorDef("C3", "성실성과 규칙준수", "COMMUNITY"),
				new IndicatorDef("D1", "자기주도성", "POTENTIAL"), new IndicatorDef("D3", "창의적 문제해결력", "POTENTIAL"));
		Set<String> ids = Set.of("A2", "A3", "B3", "C1", "C2", "C3", "D1", "D3");
		HolisticReportInput input = factory.create(defs, ids,
				Map.of("A2", 4.5, "A3", 4.5, "B3", 4.0, "C1", 4.0, "C2", 1.5, "C3", 1.5, "D1", 1.5, "D3", 1.5),
				Map.of("ACADEMIC", 4.5, "MAJOR_FIT", 4.0, "COMMUNITY", 2.5, "POTENTIAL", 1.5), 3.0, Map.of(), List.of(), 5000);

		HolisticReportContent content = template.generate(input);

		assertThat(content.strengths()).hasSize(3);
		assertThat(content.toDevelop()).hasSize(3);
		assertThat(content.nextSteps().size()).isLessThanOrEqualTo(4);
	}
}
