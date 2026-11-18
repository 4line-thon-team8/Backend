package com.fivegrade.backend.holistic.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.Coverage;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.EvidencePhrase;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.IndicatorSignal;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.InterestTag;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest.SectionChars;
import com.fivegrade.backend.holistic.rules.HolisticRuleCatalog;
import com.fivegrade.backend.holistic.scoring.DocumentContext;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.ElementLevels;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 리포트 서비스가 평가 결과를 응답으로 바꾸는 흐름을 확인한다. 평가기는 고정 값으로 대신하고 DB 없이 실행한다. */
class HolisticReportServiceTest {

	private final HolisticRuleCatalog catalog = new HolisticRuleCatalog();

	private HolisticAnalysisRequest request() {
		return new HolisticAnalysisRequest(
				catalog.rulesVersion(),
				new Coverage(new SectionChars(1000, 1000, 1000, 5000, 1000), List.of(1, 2, 3)),
				List.of(new IndicatorSignal("A3", 4, List.of(3, 1), 0.8), new IndicatorSignal("C1", 2, List.of(2), 0.5)),
				List.of(new InterestTag("생명과학", 7, List.of(1, 2))),
				List.of(new EvidencePhrase(List.of("METHOD"), "설문을 설계하여 분석함", 2, "subject")));
	}

	private HolisticEvaluation.Applied applied() {
		return new HolisticEvaluation.Applied(
				catalog.rulesVersion(),
				Map.of("A3", 4.2, "C1", 3.0),
				Map.of("ACADEMIC", 4.2, "COMMUNITY", 3.0),
				new DocumentContext(new ElementLevels(4.2, null, 3.0), 3.6));
	}

	@Test
	void 반영하지_못했으면_이유를_담아_SKIPPED로_답한다() {
		HolisticReportService service = new HolisticReportService(
				request -> new HolisticEvaluation.Skipped(HolisticEvaluation.SkipReason.TOO_SHORT), catalog,
				new HolisticReportTemplate());

		HolisticReportResponse response = service.create(request());

		assertThat(response.status()).isEqualTo("SKIPPED");
		assertThat(response.skipReason()).isEqualTo("TOO_SHORT");
		assertThat(response.report()).isNull();
		assertThat(response.elements()).isNull();
	}

	@Test
	void 반영했으면_요소_네_개와_글_리포트를_담는다() {
		HolisticReportService service = new HolisticReportService(request -> applied(), catalog, new HolisticReportTemplate());

		HolisticReportResponse response = service.create(request());

		assertThat(response.status()).isEqualTo("APPLIED");
		assertThat(response.skipReason()).isNull();
		assertThat(response.rulesVersion()).isEqualTo(catalog.rulesVersion());
		assertThat(response.elements()).hasSize(4);
		assertThat(response.report().summary().generationMethod()).isEqualTo("template");
		assertThat(response.disclaimer()).isNotNull();
	}

	@Test
	void 신호가_나타난_학년은_정렬해서_내려준다() {
		HolisticReportService service = new HolisticReportService(request -> applied(), catalog, new HolisticReportTemplate());

		HolisticReportResponse response = service.create(request());

		var a3 = response.elements().get(0).indicators().get(0);
		assertThat(a3.id()).isEqualTo("A3");
		assertThat(a3.observedYears()).containsExactly(1, 3);
	}

	@Test
	void 규칙이_없는_지표는_측정하지_않음으로_내려준다() {
		HolisticReportService service = new HolisticReportService(request -> applied(), catalog, new HolisticReportTemplate());

		HolisticReportResponse response = service.create(request());

		assertThat(response.notMeasured()).extracting(item -> item.id()).contains("B4", "D2");
	}

	@Test
	void 근거_구절은_응답과_글에_나타나지_않는다() {
		HolisticReportService service = new HolisticReportService(request -> applied(), catalog, new HolisticReportTemplate());

		HolisticReportResponse response = service.create(request());

		assertThat(response.toString()).doesNotContain("설문을 설계하여 분석함");
	}
}
