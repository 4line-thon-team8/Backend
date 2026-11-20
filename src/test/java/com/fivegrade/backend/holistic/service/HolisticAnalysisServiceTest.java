package com.fivegrade.backend.holistic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.Coverage;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.IndicatorSignal;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.SectionChars;
import com.fivegrade.backend.holistic.rules.HolisticRuleCatalog;
import com.fivegrade.backend.holistic.service.HolisticEvaluation.Applied;
import com.fivegrade.backend.holistic.service.HolisticEvaluation.SkipReason;
import com.fivegrade.backend.holistic.service.HolisticEvaluation.Skipped;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 서술 분석 요청이 서류 수준으로 바뀌는 흐름과 반영하지 않는 경우를 확인한다. DB 없이 실제 규칙 사전으로 실행한다.
 */
class HolisticAnalysisServiceTest {

	private static final double DELTA = 1e-9;

	private final HolisticRuleCatalog catalog = new HolisticRuleCatalog();
	private final HolisticRequestValidator validator = new HolisticRequestValidator(catalog, () -> Set.of("컴퓨터"));
	private final HolisticAnalysisService service = new HolisticAnalysisService(catalog, validator, true);

	@Test
	void 서술_분석을_보내지_않으면_반영하지_않는다() {
		assertThat(service.evaluate(null)).isEqualTo(new Skipped(SkipReason.NOT_PROVIDED));
	}

	@Test
	void 기능이_꺼져_있으면_보내도_반영하지_않는다() {
		HolisticAnalysisService disabled = new HolisticAnalysisService(catalog, validator, false);

		assertThat(disabled.evaluate(request("0.1.0", chars(300, 300), strongSignals())))
				.isEqualTo(new Skipped(SkipReason.DISABLED));
	}

	@Test
	void 규칙_버전이_다르면_검증하지_않고_반영하지_않는다() {
		// 버전이 다르면 지표 ID가 달라졌을 수 있어 사전 대조 검증을 하지 않는다. 없는 지표가 있어도 400이 아니다.
		HolisticAnalysisRequest outdated = request("local-0.1", chars(300, 300),
				List.of(new IndicatorSignal("ZZ", 1, List.of(1), 0.5)));

		assertThat(service.evaluate(outdated)).isEqualTo(new Skipped(SkipReason.RULES_OUTDATED));
	}

	@Test
	void 사전에_없는_지표가_있으면_400이다() {
		HolisticAnalysisRequest invalid = request("0.1.0", chars(300, 300),
				List.of(new IndicatorSignal("ZZ", 1, List.of(1), 0.5)));

		assertThatThrownBy(() -> service.evaluate(invalid)).isInstanceOf(BusinessException.class);
	}

	@Test
	void 읽은_서술이_적으면_반영하지_않는다() {
		assertThat(service.evaluate(request("0.1.0", chars(100, 100), strongSignals())))
				.isEqualTo(new Skipped(SkipReason.TOO_SHORT));
	}

	@Test
	void 신호가_하나도_없으면_반영하지_않는다() {
		assertThat(service.evaluate(request("0.1.0", chars(300, 300), List.of())))
				.isEqualTo(new Skipped(SkipReason.INSUFFICIENT_SIGNALS));
	}

	@Test
	void 신호가_충분하면_요소_수준과_전체_서류_수준을_만든다() {
		HolisticEvaluation result = service.evaluate(request("0.1.0", chars(300, 300), strongSignals()));

		assertThat(result).isInstanceOf(Applied.class);
		Applied applied = (Applied) result;
		assertThat(applied.rulesVersion()).isEqualTo("0.1.0");
		// 신호가 관찰된 지표(학업 A2·A3, 진로 B3, 공동체 C1~C4)는 모두 5.0
		assertThat(applied.elementLevels().get("ACADEMIC")).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
		assertThat(applied.elementLevels().get("MAJOR_FIT")).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
		assertThat(applied.elementLevels().get("COMMUNITY")).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
		// 신호가 없는 발전가능성(D4)은 관찰되지 않은 것이라 수준 자체가 없다.
		assertThat(applied.elementLevels()).doesNotContainKey("POTENTIAL");
		assertThat(applied.overallLevel()).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
	}

	@Test
	void 신호가_없는_지표와_요소는_평균에_넣지_않는다() {
		HolisticEvaluation result = service.evaluate(request("0.1.0", chars(300, 300),
				List.of(new IndicatorSignal("A3", 7, List.of(1, 2), 1.0),
						new IndicatorSignal("C1", 7, List.of(1, 2), 1.0))));

		Applied applied = (Applied) result;
		// 학업은 A2가 관찰되지 않아도 A3(5.0)만으로 5.0, 진로는 관찰된 지표가 없어 제외, 공동체 5.0
		assertThat(applied.elementLevels().get("ACADEMIC")).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
		assertThat(applied.elementLevels()).doesNotContainKey("MAJOR_FIT");
		assertThat(applied.overallLevel()).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
	}

	@Test
	void 신호가_한_축에만_있어도_그_축으로_서류_수준을_만든다() {
		HolisticEvaluation result = service.evaluate(request("0.1.0", chars(300, 300),
				List.of(new IndicatorSignal("A3", 7, List.of(1, 2), 1.0))));

		assertThat(result).isInstanceOf(Applied.class);
		assertThat(((Applied) result).overallLevel()).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
	}

	@Test
	void 서류_3축에_해당하는_신호가_없으면_발전가능성만_있어도_반영하지_않는다() {
		// D4(발전가능성)는 서류 수준의 3축에 들어가지 않는다.
		assertThat(service.evaluate(request("0.1.0", chars(300, 300),
				List.of(new IndicatorSignal("D4", 7, List.of(1, 2), 1.0)))))
				.isEqualTo(new Skipped(SkipReason.INSUFFICIENT_SIGNALS));
	}

	@Test
	void 같은_신호라도_서술이_길면_서류_수준이_내려간다() {
		List<IndicatorSignal> signals = List.of(
				new IndicatorSignal("A3", 9, List.of(1, 2), 0.7),
				new IndicatorSignal("B3", 9, List.of(1, 2), 0.7),
				new IndicatorSignal("C1", 9, List.of(1, 2), 0.7));

		Applied shortDoc = (Applied) service.evaluate(request("0.1.0", chars(1_500, 1_500), signals));
		Applied longDoc = (Applied) service.evaluate(request("0.1.0", chars(25_000, 25_000), signals));

		// 품질은 실제 문서 범위(약 0.6~0.75)로 둔다. 하위 5% 아래의 약한 문서는 둘 다 1.0 으로 막혀 비교할 수 없다.
		assertThat(shortDoc.overallLevel()).isGreaterThan(longDoc.overallLevel());
	}

	private static List<IndicatorSignal> strongSignals() {
		return List.of(
				new IndicatorSignal("A2", 7, List.of(1, 2), 1.0),
				new IndicatorSignal("A3", 7, List.of(1, 2), 1.0),
				new IndicatorSignal("B3", 7, List.of(1, 2), 1.0),
				new IndicatorSignal("C1", 7, List.of(1, 2), 1.0),
				new IndicatorSignal("C2", 7, List.of(1, 2), 1.0),
				new IndicatorSignal("C3", 7, List.of(1, 2), 1.0),
				new IndicatorSignal("C4", 7, List.of(1, 2), 1.0));
	}

	private static SectionChars chars(int subject, int club) {
		return new SectionChars(0, club, 0, subject, 0);
	}

	private static HolisticAnalysisRequest request(String version, SectionChars chars, List<IndicatorSignal> signals) {
		return new HolisticAnalysisRequest(version, new Coverage(chars, List.of(1, 2)), signals, List.of(), List.of());
	}

}
