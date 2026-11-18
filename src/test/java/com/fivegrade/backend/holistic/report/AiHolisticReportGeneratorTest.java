package com.fivegrade.backend.holistic.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.holistic.report.HolisticReportInputFactory.IndicatorDef;
import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import com.fivegrade.backend.report.generation.ReportSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AI 결과를 쓰는 경우와 기본 문장으로 대신하는 경우를 가짜 클라이언트로 확인한다. */
class AiHolisticReportGeneratorTest {

	private static final List<IndicatorDef> DEFS = List.of(
			new IndicatorDef("A3", "탐구력", "ACADEMIC"),
			new IndicatorDef("B3", "진로 탐색 활동과 경험", "MAJOR_FIT"),
			new IndicatorDef("C1", "협업과 소통능력", "COMMUNITY"));

	private static final HolisticReportInput INPUT = new HolisticReportInputFactory().create(
			DEFS, Set.of("A3", "B3", "C1"),
			Map.of("A3", 4.4, "B3", 2.0), Map.of("ACADEMIC", 4.4, "MAJOR_FIT", 2.0),
			3.0, Map.of("A3", List.of(2)), List.of(), 5000);

	private static ParsedReport valid() {
		return new ParsedReport(
				new Line("탐구하는 모습이 비교적 잘 드러나는 기록이에요.", List.of("ACADEMIC")),
				List.of(new Line("탐구력이 잘 드러나는 것으로 보여요.", List.of("A3"))),
				List.of(new Line("진로 탐색 활동과 경험은 근거가 더 필요해 보여요.", List.of("B3"))),
				List.of(new Line("진로와 이어진 활동의 과정을 구체적으로 기록해 보세요.", List.of("B3"))));
	}

	private final HolisticReportTemplate template = new HolisticReportTemplate();

	private static List<String> texts(HolisticReportContent content) {
		List<ReportSection> all = new ArrayList<>();
		all.add(content.summary());
		all.addAll(content.strengths());
		all.addAll(content.toDevelop());
		all.addAll(content.nextSteps());
		all.add(content.caveat());
		return all.stream().map(ReportSection::text).toList();
	}

	@Test
	void 검증을_통과하면_AI_문장을_쓰고_안내는_서버_고정_문장이다() {
		HolisticReportContent content = new AiHolisticReportGenerator(input -> valid(), template).generate(INPUT);

		assertThat(content.summary().generationMethod()).isEqualTo("llm");
		assertThat(content.strengths()).extracting(ReportSection::generationMethod).containsOnly("llm");
		assertThat(content.toDevelop()).hasSize(1);
		assertThat(content.caveat().generationMethod()).isEqualTo("template");
		assertThat(content.caveat().text()).isEqualTo(HolisticReportTemplate.caveatText(INPUT));
	}

	@Test
	void 검증에_실패하면_전체를_기본_문장으로_대신한다() {
		ParsedReport forged = new ParsedReport(valid().summary(),
				List.of(new Line("탐구력이 4점이라 합격에 유리해요.", List.of("A3"))),
				valid().toDevelop(), valid().nextSteps());

		HolisticReportContent content = new AiHolisticReportGenerator(input -> forged, template).generate(INPUT);

		assertThat(texts(content)).isEqualTo(texts(template.generate(INPUT)));
		assertThat(content.summary().generationMethod()).isEqualTo("template");
	}

	@Test
	void 호출이_실패하면_기본_문장으로_대신하고_예외를_내지_않는다() {
		HolisticReportContent content = new AiHolisticReportGenerator(input -> {
			throw new IllegalStateException("provider down");
		}, template).generate(INPUT);

		assertThat(texts(content)).isEqualTo(texts(template.generate(INPUT)));
	}

	@Test
	void AI에_보내는_입력에는_수준_수치와_원문과_이름이_없다() {
		Map<String, Object> sent = HolisticReportPrompt.inputOf(INPUT);
		String flat = sent.toString();

		assertThat(flat).contains("HIGH", "LOW", "탐구력", "notObserved");
		assertThat(sent).doesNotContainKeys("level", "evidence", "evidences", "name", "school", "grades");
		assertThat(flat).doesNotContain("level=", "4.4", "evidence");
	}
}
