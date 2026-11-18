package com.fivegrade.backend.holistic.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.holistic.report.HolisticReportInputFactory.IndicatorDef;
import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AI 초안을 거르는 규칙을 확인한다. 위조된 근거·단정 표현·금지어는 모두 거절되어야 한다. */
class HolisticReportValidatorTest {

	private static final List<IndicatorDef> DEFS = List.of(
			new IndicatorDef("A2", "학업태도", "ACADEMIC"),
			new IndicatorDef("A3", "탐구력", "ACADEMIC"),
			new IndicatorDef("B3", "진로 탐색 활동과 경험", "MAJOR_FIT"),
			new IndicatorDef("C1", "협업과 소통능력", "COMMUNITY"),
			new IndicatorDef("D2", "경험의 다양성", "POTENTIAL"));

	/** A3 높음, B3 낮음, A2·C1 관찰되지 않음, D2 측정하지 않음 */
	private static final HolisticReportInput INPUT = new HolisticReportInputFactory().create(
			DEFS, Set.of("A2", "A3", "B3", "C1"),
			Map.of("A3", 4.4, "B3", 2.0),
			Map.of("ACADEMIC", 4.4, "MAJOR_FIT", 2.0),
			3.0, Map.of(), List.of(), 5000);

	private static Line line(String text, String... refs) {
		return new Line(text, List.of(refs));
	}

	private static ParsedReport report(Line summary, List<Line> strengths, List<Line> toDevelop, List<Line> nextSteps) {
		return new ParsedReport(summary, strengths, toDevelop, nextSteps);
	}

	private static ParsedReport good() {
		return report(
				line("탐구하는 모습이 비교적 잘 드러나는 기록이에요.", "ACADEMIC"),
				List.of(line("탐구력이 잘 드러나는 것으로 보여요.", "A3")),
				List.of(line("진로 탐색 활동과 경험은 근거가 더 필요해 보여요.", "B3")),
				List.of(line("진로와 이어진 활동의 과정을 구체적으로 기록해 보세요.", "B3")));
	}

	private static ParsedReport withStrength(Line strength) {
		return report(good().summary(), List.of(strength), good().toDevelop(), good().nextSteps());
	}

	private static ParsedReport withToDevelop(Line toDevelop) {
		return report(good().summary(), good().strengths(), List.of(toDevelop), good().nextSteps());
	}

	private static String reject(ParsedReport report) {
		return HolisticReportValidator.rejection(INPUT, report);
	}

	@Test
	void 입력과_맞는_초안은_통과한다() {
		assertThat(reject(good())).isNull();
		assertThat(HolisticReportValidator.isValid(INPUT, good())).isTrue();
	}

	@Test
	void 입력에_없는_근거_id는_거절한다() {
		assertThat(reject(withStrength(line("탐구력이 잘 드러나는 것으로 보여요.", "A9")))).isEqualTo("UNKNOWN_REF");
	}

	@Test
	void 측정하지_않는_지표_id는_근거로_받지_않는다() {
		assertThat(reject(withToDevelop(line("관련 내용은 근거가 더 필요해 보여요.", "D2")))).isEqualTo("UNKNOWN_REF");
	}

	@Test
	void 근거_id가_없으면_거절한다() {
		assertThat(reject(withStrength(line("탐구력이 잘 드러나는 것으로 보여요.")))).isEqualTo("NO_REFS");
	}

	@Test
	void 낮은_구간이나_관찰되지_않은_지표를_강점으로_쓰면_거절한다() {
		assertThat(reject(withStrength(line("진로 탐색 활동과 경험이 잘 드러나요.", "B3")))).isEqualTo("REF_BAND");
		assertThat(reject(withStrength(line("학업태도가 잘 드러나요.", "A2")))).isEqualTo("REF_BAND");
	}

	@Test
	void 높은_구간_지표를_보완점으로_쓰면_거절한다() {
		assertThat(reject(withToDevelop(line("탐구력은 근거가 더 필요해 보여요.", "A3")))).isEqualTo("REF_BAND");
	}

	@Test
	void 관찰되지_않은_지표는_보완점_근거로_쓸_수_있다() {
		assertThat(reject(withToDevelop(line("협업과 소통능력은 이번 분석에서 뚜렷이 확인되지 않았어요.", "C1")))).isNull();
	}

	@Test
	void 단정적인_부정_표현은_거절한다() {
		for (String text : List.of(
				"협업과 소통능력이 부족해요.",
				"협업과 소통능력은 없어요.",
				"협업과 소통능력이 약해 보여요.",
				"협업과 소통능력이 낮은 편이에요.")) {
			assertThat(reject(withToDevelop(line(text, "C1")))).as(text).isEqualTo("FORBIDDEN");
		}
	}

	@Test
	void 숫자_합격_전망_대학_이름은_거절한다() {
		for (String text : List.of(
				"탐구력이 4점으로 높아요.",
				"탐구력이 좋아서 합격에 유리해요.",
				"탐구력을 보면 대학 평가에서 좋아 보여요.",
				"탐구력이 상위 10%에 가까워요.",
				"탐구력은 확실히 돋보여요.")) {
			assertThat(reject(withStrength(line(text, "A3")))).as(text).isEqualTo("FORBIDDEN");
		}
	}

	@Test
	void 링크와_줄바꿈과_목록_기호는_거절한다() {
		assertThat(reject(withStrength(line("탐구력이 보여요. https://x.kr", "A3")))).isEqualTo("FORBIDDEN");
		assertThat(reject(withStrength(line("탐구력이 보여요.\n더 보여요.", "A3")))).isEqualTo("FORMAT");
		assertThat(reject(withStrength(line("- 탐구력이 보여요.", "A3")))).isEqualTo("FORMAT");
	}

	@Test
	void 해요체가_아니면_거절한다() {
		assertThat(reject(withStrength(line("탐구력이 잘 드러난다.", "A3")))).isEqualTo("STYLE");
		assertThat(reject(withStrength(line("탐구력이 잘 드러나요", "A3")))).isEqualTo("STYLE");
	}

	@Test
	void 길이가_비었거나_너무_길면_거절한다() {
		assertThat(reject(withStrength(line("  ", "A3")))).isEqualTo("TEXT_LENGTH");
		assertThat(reject(withStrength(line("탐구력이 ".repeat(40) + "보여요.", "A3")))).isEqualTo("TEXT_LENGTH");
	}

	@Test
	void 문장에_나온_지표가_근거_id의_지표가_아니면_거절한다() {
		assertThat(reject(withStrength(line("학업태도가 잘 드러나요.", "A3")))).isEqualTo("LABEL_MISMATCH");
	}

	@Test
	void 요소_id를_근거로_쓰면_그_요소의_지표_이름을_쓸_수_있다() {
		assertThat(reject(withStrength(line("탐구력이 잘 드러나 학업 쪽이 탄탄해 보여요.", "ACADEMIC")))).isNull();
	}

	@Test
	void 측정하지_않는_지표_이름을_쓰면_거절한다() {
		assertThat(reject(withStrength(line("탐구력과 경험의 다양성이 드러나요.", "A3")))).isEqualTo("NOT_MEASURED");
	}

	@Test
	void 같은_문장을_반복하면_거절한다() {
		ParsedReport report = report(good().summary(), good().strengths(),
				List.of(line("진로 탐색 활동과 경험은 근거가 더 필요해 보여요.", "B3")),
				List.of(line("진로 탐색 활동과 경험은 근거가 더 필요해 보여요.", "B3")));
		assertThat(reject(report)).isEqualTo("DUPLICATE");
	}

	@Test
	void 개수가_한도를_넘으면_거절한다() {
		Line strength = line("탐구력이 잘 드러나요.", "A3");
		ParsedReport report = report(good().summary(), List.of(strength, strength, strength, strength),
				good().toDevelop(), good().nextSteps());
		assertThat(reject(report)).isEqualTo("COUNT");
	}

	@Test
	void 보완할_항목이_없으면_제안은_어떤_근거든_쓸_수_있다() {
		HolisticReportInput allHigh = new HolisticReportInputFactory().create(
				List.of(new IndicatorDef("A3", "탐구력", "ACADEMIC")), Set.of("A3"),
				Map.of("A3", 4.4), Map.of("ACADEMIC", 4.4), 4.4, Map.of(), List.of(), 5000);
		ParsedReport report = report(line("전반적으로 안정적으로 드러나요.", "ACADEMIC"),
				List.of(line("탐구력이 잘 드러나요.", "A3")), List.of(),
				List.of(line("탐구 과정을 계속 구체적으로 기록해 보세요.", "A3")));
		assertThat(HolisticReportValidator.rejection(allHigh, report)).isNull();
	}
}
