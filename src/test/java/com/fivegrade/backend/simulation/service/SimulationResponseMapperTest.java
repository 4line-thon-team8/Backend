package com.fivegrade.backend.simulation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.report.generation.ConsultingReport;
import com.fivegrade.backend.report.generation.ReportSection;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import com.fivegrade.backend.simulation.dto.SimulationReportResult.ExplainedCard;
import com.fivegrade.backend.simulation.dto.SimulationResponse;
import com.fivegrade.backend.simulation.dto.SimulationResponse.WarningItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 공개 응답의 안내(warnings) 정리 규칙(#74)을 확인한다. */
class SimulationResponseMapperTest {

	@Test
	void 선호_전형_안내는_응답에서_제외된다() {
		SimulationResponse response = toResponse(
				List.of(card(1, CandidateKind.OTHER_TRACK)),
				List.of(RecommendationWarning.forCard(WarningCode.OTHER_TRACK_USED, 1)));

		assertEquals(List.of(), codes(response));
		assertEquals("OTHER_TRACK", response.recommendations().getFirst().candidateKind());
	}

	@Test
	void 유사_학과_안내는_카드가_여럿이어도_결과_단위로_한_번만_내려간다() {
		SimulationResponse response = toResponse(
				List.of(card(1, CandidateKind.SIMILAR_MAJOR), card(2, CandidateKind.SIMILAR_MAJOR)),
				List.of(
						RecommendationWarning.forCard(WarningCode.SIMILAR_MAJOR_USED, 1),
						RecommendationWarning.forCard(WarningCode.SIMILAR_MAJOR_USED, 2)));

		assertEquals(List.of("SIMILAR_MAJOR_USED"), codes(response));
		assertEquals(null, response.warnings().getFirst().rank());
	}

	@Test
	void 범위_확장_안내는_발생한_코드만_정해진_순서로_내려간다() {
		SimulationResponse response = toResponse(
				List.of(card(1, CandidateKind.UNDECLARED), card(2, CandidateKind.SIMILAR_MAJOR)),
				List.of(
						RecommendationWarning.forCard(WarningCode.UNDECLARED_USED, 1),
						RecommendationWarning.forCard(WarningCode.SIMILAR_MAJOR_USED, 2)));

		assertEquals(List.of("SIMILAR_MAJOR_USED", "UNDECLARED_USED"), codes(response));
	}

	@Test
	void 결과_전체_안내가_먼저_나오고_카드별_안내는_그대로_뒤에_붙는다() {
		SimulationResponse response = toResponse(
				List.of(card(1, CandidateKind.SIMILAR_MAJOR), card(2, CandidateKind.DESIRED_MAJOR)),
				List.of(
						RecommendationWarning.forResult(WarningCode.FEWER_THAN_SIX_CARDS),
						RecommendationWarning.forCard(WarningCode.SIMILAR_MAJOR_USED, 1),
						RecommendationWarning.forCard(WarningCode.LOW_CONFIDENCE_CARD, 2)));

		assertEquals(List.of("FEWER_THAN_SIX_CARDS", "SIMILAR_MAJOR_USED", "LOW_CONFIDENCE_CARD"), codes(response));
		assertEquals(2, response.warnings().get(2).rank());
	}

	@Test
	void 범위를_넓히지_않았으면_범위_확장_안내가_없다() {
		SimulationResponse response = toResponse(List.of(card(1, CandidateKind.DESIRED_MAJOR)), List.of());

		assertEquals(List.of(), response.warnings());
	}

	private List<String> codes(SimulationResponse response) {
		return response.warnings().stream().map(WarningItem::code).toList();
	}

	private SimulationResponse toResponse(List<RecommendedCard> cards, List<RecommendationWarning> warnings) {
		ReportSection section = ReportSection.template("문구");
		return SimulationResponseMapper.toResponse(new SimulationReportResult(
				new BigDecimal("1.13"),
				List.of(new SimulationAnalysis.SubjectGrade("row-1", "KOREAN", new BigDecimal("1.00"))),
				cards.stream().map(card -> new ExplainedCard(card, section)).toList(),
				warnings,
				new ConsultingReport(section, section, section, section)));
	}

	private RecommendedCard card(int rank, CandidateKind kind) {
		return new RecommendedCard(rank, rank * 10L, "가대학교", "컴퓨터공학과", "학생부교과(교과우수자)",
				AdmissionBand.MATCH, kind, 0.5, new CardEvidence(30, 4.5, false, 3));
	}

}
