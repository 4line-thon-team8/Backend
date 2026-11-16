package com.fivegrade.backend.simulation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.holistic.scoring.DocumentContext;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.ElementLevels;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import com.fivegrade.backend.holistic.service.HolisticEvaluation.SkipReason;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.report.generation.TemplateReportGenerator;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 서술 분석을 반영하지 못했을 때의 안내와 결과 전달을 확인한다. DB나 Spring 없이 실행한다. */
class SimulationReportAssemblerHolisticTest {

	private final SimulationReportAssembler assembler = new SimulationReportAssembler(new TemplateReportGenerator());

	@Test
	void 서술_분석을_보냈는데_반영하지_못했으면_결과_전체_안내를_붙인다() {
		for (SkipReason reason : List.of(
				SkipReason.DISABLED, SkipReason.RULES_OUTDATED, SkipReason.TOO_SHORT, SkipReason.INSUFFICIENT_SIGNALS)) {
			SimulationReportResult result = assembler.assemble(analysis(new HolisticEvaluation.Skipped(reason)));

			assertTrue(result.warnings().stream()
					.anyMatch(w -> w.code() == WarningCode.HOLISTIC_SKIPPED && w.rank() == null), reason.name());
		}
	}

	@Test
	void 보내지_않았거나_반영했으면_안내를_붙이지_않는다() {
		SimulationReportResult notProvided = assembler.assemble(
				analysis(new HolisticEvaluation.Skipped(SkipReason.NOT_PROVIDED)));
		SimulationReportResult applied = assembler.assemble(analysis(new HolisticEvaluation.Applied(
				"0.3.0", Map.of(), Map.of("ACADEMIC", 4.0),
				new DocumentContext(new ElementLevels(4.0, 3.0, 3.0), 3.3))));

		assertTrue(notProvided.warnings().stream().noneMatch(w -> w.code() == WarningCode.HOLISTIC_SKIPPED));
		assertTrue(applied.warnings().stream().noneMatch(w -> w.code() == WarningCode.HOLISTIC_SKIPPED));
	}

	@Test
	void 서술_분석_결과는_내부_결과에_그대로_전달한다() {
		HolisticEvaluation skipped = new HolisticEvaluation.Skipped(SkipReason.TOO_SHORT);

		assertEquals(skipped, assembler.assemble(analysis(skipped)).holistic());
	}

	private SimulationAnalysis analysis(HolisticEvaluation holistic) {
		RecommendedCard card = new RecommendedCard(1, 10L, "가대학교", "컴퓨터공학과", "학생부종합",
				AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR, 0.5, new CardEvidence(30, 4.5, false, 3));
		return new SimulationAnalysis(
				new BigDecimal("1.13"),
				List.of(new SimulationAnalysis.SubjectGrade("row-1", "SUBJ_0001", new BigDecimal("1.00"))),
				new RecommendationResult(List.of(card), List.of()),
				holistic);
	}

}
