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
	void 신호가_하나도_없으면_최저_수준으로_보지_않고_반영하지_않는다() {
		assertThat(service.evaluate(request("0.1.0", chars(300, 300), List.of())))
				.isEqualTo(new Skipped(SkipReason.INSUFFICIENT_SIGNALS));
	}

	@Test
	void 신호가_충분하면_요소_수준과_전체_서류_수준을_만든다() {
		HolisticEvaluation result = service.evaluate(request("0.1.0", chars(300, 300), strongSignals()));

		assertThat(result).isInstanceOf(Applied.class);
		Applied applied = (Applied) result;
		assertThat(applied.rulesVersion()).isEqualTo("0.1.0");
		// 규칙이 있는 지표(학업 A2·A3, 진로 B3, 공동체 C1~C4)에 모두 강한 신호가 있으면 각 5.0. 평균이 규칙 수에 흔들리지 않게 전부 채운다.
		assertThat(applied.elementLevels().get("ACADEMIC")).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
		assertThat(applied.elementLevels().get("MAJOR_FIT")).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
		assertThat(applied.elementLevels().get("COMMUNITY")).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
		// 규칙은 있는데 신호가 없는 발전가능성(D4)은 근거 없음으로 1.0
		assertThat(applied.elementLevels().get("POTENTIAL")).isEqualTo(1.0, org.assertj.core.data.Offset.offset(DELTA));
		assertThat(applied.overallLevel()).isEqualTo(5.0, org.assertj.core.data.Offset.offset(DELTA));
	}

	@Test
	void 신호가_일부_요소에만_있으면_나머지_요소는_최저_수준으로_평균에_들어간다() {
		HolisticEvaluation result = service.evaluate(request("0.1.0", chars(300, 300),
				List.of(new IndicatorSignal("A3", 7, List.of(1, 2), 1.0))));

		Applied applied = (Applied) result;
		// 학업은 A2(근거 없음 1.0)와 A3(5.0)의 평균 3.0, 진로 1.0, 공동체 1.0 → 균등 평균 5/3
		assertThat(applied.overallLevel()).isEqualTo(5.0 / 3.0, org.assertj.core.data.Offset.offset(DELTA));
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
