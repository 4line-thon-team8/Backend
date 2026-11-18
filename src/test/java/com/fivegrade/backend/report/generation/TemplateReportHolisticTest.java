package com.fivegrade.backend.report.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.holistic.scoring.DocumentContext;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.ElementLevels;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.DocumentFit;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 서술 분석 결과가 템플릿 리포트에 반영되는지 확인한다. */
class TemplateReportHolisticTest {

	private static final CardEvidence EVIDENCE = new CardEvidence(10, 3.0, false, 3);
	private final TemplateReportGenerator generator = new TemplateReportGenerator();

	private HolisticEvaluation.Applied applied() {
		return new HolisticEvaluation.Applied("0.7.0", Map.of(),
				Map.of("ACADEMIC", 4.1, "COMMUNITY", 2.2, "MAJOR_FIT", 3.0),
				new DocumentContext(new ElementLevels(4.1, 3.0, 2.2), 3.4));
	}

	private RecommendationResult result(DocumentFit fit) {
		return new RecommendationResult(List.of(new RecommendedCard(1, 1L, "테스트대학교", "테스트학과", "학생부종합",
				AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR, 0.1, EVIDENCE, null, fit)), List.of());
	}

	@Test
	void appliedAnalysisAddsStrengthAndWeaknessToAssessment() {
		String text = generator.generate(new BigDecimal("2.0"), result(null), applied())
				.report().overallAssessment().text();

		assertTrue(text.contains("학업역량이 잘 드러나는 편"));
		assertTrue(text.contains("공동체역량은 근거가 더 필요"));
	}

	@Test
	void cardReasonMentionsDocumentFitOnlyWhenPresent() {
		var withFit = generator.generate(new BigDecimal("2.0"),
				result(DocumentFit.of(4.5, List.of("ACADEMIC"))), applied());
		var withoutFit = generator.generate(new BigDecimal("2.0"), result(null), applied());

		assertTrue(withFit.reasons().get(0).reason().text().contains("학업역량 측면에서 강점"));
		assertFalse(withoutFit.reasons().get(0).reason().text().contains("분석한 생기부"));
	}

	@Test
	void withoutAppliedAnalysisOutputEqualsLegacy() {
		var legacy = generator.generate(new BigDecimal("2.0"), result(null));
		var skipped = generator.generate(new BigDecimal("2.0"), result(null),
				new HolisticEvaluation.Skipped(HolisticEvaluation.SkipReason.NOT_PROVIDED));

		assertEquals(legacy, skipped);
	}

	@Test
	void reportNeverContainsDigitsFromAnalysis() {
		String text = generator.generate(new BigDecimal("2.0"), result(null), applied())
				.report().overallAssessment().text();
		String added = text.substring(text.indexOf("작성하신"));

		assertFalse(added.matches(".*\\d.*"));
	}
}
