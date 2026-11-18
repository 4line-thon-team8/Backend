package com.fivegrade.backend.simulation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fivegrade.backend.holistic.scoring.DocumentContext;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.DocumentFit;
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
import java.util.Map;
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

	@Test
	void 서술_분석_결과가_없으면_NOT_PROVIDED로_내려간다() {
		SimulationResponse response = toResponse(List.of(card(1, CandidateKind.DESIRED_MAJOR)), List.of());

		assertEquals("NOT_PROVIDED", response.holistic().status());
		assertNull(response.holistic().skipReason());
		assertNull(response.holistic().elementLevels());
		assertNull(response.recommendations().getFirst().overallCategory());
		assertNull(response.recommendations().getFirst().documentFit());
	}

	@Test
	void 서술_분석을_반영하면_요소_수준과_전체_수준을_둘째_자리로_내린다() {
		var levels = new com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.ElementLevels(4.0, 2.0, null);
		var applied = new HolisticEvaluation.Applied("0.3.0",
				Map.of("A3", 4.0), Map.of("ACADEMIC", 4.0, "MAJOR_FIT", 2.3333333),
				new DocumentContext(levels, 3.0000001));

		SimulationResponse response = toResponse(List.of(card(1, CandidateKind.DESIRED_MAJOR)), List.of(), applied);

		assertEquals("APPLIED", response.holistic().status());
		assertEquals("0.3.0", response.holistic().rulesVersion());
		assertEquals(new BigDecimal("4.00"), response.holistic().elementLevels().get("ACADEMIC"));
		assertEquals(new BigDecimal("2.33"), response.holistic().elementLevels().get("MAJOR_FIT"));
		assertEquals(new BigDecimal("3.00"), response.holistic().overallLevel());
		assertNull(response.holistic().skipReason());
		// A3 수준 4.0 은 HIGH 이고 신뢰도 MID 이므로 강점으로 내려간다. 활동 요약·학과별 조언은 아직 없다.
		assertEquals(1, response.holistic().strengths().size());
		assertEquals("A3", response.holistic().strengths().getFirst().indicatorId());
		assertEquals(4, response.holistic().strengths().getFirst().level());
		assertNull(response.holistic().activitySummary());
		assertEquals(List.of(), response.holistic().majorAdvices());
	}

	@Test
	void 반영하지_못했으면_SKIPPED와_이유를_내리고_NOT_PROVIDED는_보내지_않은_것으로_본다() {
		SimulationResponse skipped = toResponse(List.of(), List.of(),
				new HolisticEvaluation.Skipped(HolisticEvaluation.SkipReason.TOO_SHORT));
		SimulationResponse notProvided = toResponse(List.of(), List.of(),
				new HolisticEvaluation.Skipped(HolisticEvaluation.SkipReason.NOT_PROVIDED));

		assertEquals("SKIPPED", skipped.holistic().status());
		assertEquals("TOO_SHORT", skipped.holistic().skipReason());
		assertEquals("NOT_PROVIDED", notProvided.holistic().status());
		for (var holistic : List.of(skipped.holistic(), notProvided.holistic())) {
			assertNull(holistic.activitySummary());
			assertEquals(List.of(), holistic.strengths());
			assertEquals(List.of(), holistic.majorAdvices());
		}
	}

	@Test
	void 서류_보정을_적용한_카드는_category가_성적_구간이고_overallCategory가_보정_후_구간이다() {
		RecommendedCard adjusted = new RecommendedCard(1, 10L, "가대학교", "컴퓨터공학과", "학생부종합",
				AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR, 0.5, new CardEvidence(30, 4.5, false, 3),
				AdmissionBand.REACH, DocumentFit.of(4.5, List.of("ACADEMIC")));

		var item = toResponse(List.of(adjusted), List.of()).recommendations().getFirst();

		assertEquals("REACH", item.category());
		assertEquals("MATCH", item.overallCategory());
		assertEquals("HIGH", item.documentFit().level());
		assertEquals(List.of("ACADEMIC"), item.documentFit().basis());
	}

	private SimulationResponse toResponse(
			List<RecommendedCard> cards, List<RecommendationWarning> warnings, HolisticEvaluation holistic) {
		ReportSection section = ReportSection.template("문구");
		return SimulationResponseMapper.toResponse(new SimulationReportResult(
				new BigDecimal("1.13"),
				List.of(new SimulationAnalysis.SubjectGrade("row-1", "KOREAN", new BigDecimal("1.00"))),
				cards.stream().map(card -> new ExplainedCard(card, section)).toList(),
				warnings,
				new ConsultingReport(section, section, section, section),
				holistic));
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
