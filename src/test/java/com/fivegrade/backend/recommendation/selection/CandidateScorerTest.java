package com.fivegrade.backend.recommendation.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.recommendation.candidate.CandidateUnit;
import com.fivegrade.backend.recommendation.candidate.UnitYearData;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.recommendation.scale.GradeScaleConverter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 후보의 구간·gradeGap·근거 지표·신뢰도 계산을 검증한다. DB나 Spring 없이 실행한다.
 */
class CandidateScorerTest {

	private static final double DELTA = 1e-9;
	private static final List<Integer> RECENT_YEARS = List.of(2026, 2025, 2024);

	private final CandidateScorer scorer = new CandidateScorer();
	private final GradeScaleConverter converter = new GradeScaleConverter();

	@Test
	void gradeGapIsFiveGradeCutMinusUnroundedAverage() {
		double fiveAverage = 1.5;
		double studentNine = converter.toNineGradeEquivalent(fiveAverage);
		CandidateUnit unit = unit("교과", List.of(
				cutYear(2026, 2.6, 3.0),
				cutYear(2025, 2.6, 3.0),
				cutYear(2024, 2.6, 3.0)
		));

		ScoredCandidate scored = scorer.score(unit, RECENT_YEARS, studentNine, fiveAverage);

		assertEquals(converter.toFiveGrade(3.0) - fiveAverage, scored.gradeGap(), DELTA);
		assertEquals(3, scored.evidence().basisYears());
		assertNotEquals(AdmissionBand.UNRATED, scored.band());
		assertTrue(scored.probability() >= 0.0 && scored.probability() <= 1.0);
	}

	@Test
	void unitWithoutAnyCutIsUnrated() {
		CandidateUnit unit = unit("교과", List.of(
				new UnitYearData(2027, 10, null, null, null, null)
		));

		ScoredCandidate scored = scorer.score(unit, RECENT_YEARS, 5.0, 3.0);

		assertEquals(AdmissionBand.UNRATED, scored.band());
		assertNull(scored.gradeGap());
		assertNull(scored.probability());
		assertFalse(scored.lowConfidence());
		assertEquals(0, scored.evidence().basisYears());
	}

	@Test
	void yearWithOnlyOneCutIsNotUsed() {
		CandidateUnit unit = unit("교과", List.of(
				new UnitYearData(2026, 10, 3.0, false, null, 2.5)
		));

		ScoredCandidate scored = scorer.score(unit, RECENT_YEARS, 5.0, 3.0);

		assertEquals(AdmissionBand.UNRATED, scored.band());
	}

	@Test
	void fallbackOnlyUnitIsRatedWithLowConfidence() {
		// 50%컷이 70%컷보다 나쁜 역전 행뿐이라 정상 컷이 없다.
		CandidateUnit unit = unit("교과", List.of(cutYear(2026, 3.5, 3.0)));

		ScoredCandidate scored = scorer.score(unit, RECENT_YEARS, 5.0, 3.0);

		assertNotEquals(AdmissionBand.UNRATED, scored.band());
		assertEquals(0, scored.evidence().basisYears());
		assertTrue(scored.lowConfidence());
	}

	@Test
	void confidenceFollowsSigmaNotJustYearCount() {
		ScoredCandidate oneYear = scorer.score(
				unit("교과", List.of(cutYear(2026, 2.8, 3.0))), RECENT_YEARS, 5.0, 3.0);
		ScoredCandidate twoYearsNarrow = scorer.score(
				unit("교과", List.of(cutYear(2026, 2.8, 3.0), cutYear(2025, 2.8, 3.0))), RECENT_YEARS, 5.0, 3.0);
		ScoredCandidate twoYearsWide = scorer.score(
				unit("교과", List.of(cutYear(2026, 1.6, 3.0), cutYear(2025, 1.6, 3.0))), RECENT_YEARS, 5.0, 3.0);
		ScoredCandidate threeYearsWide = scorer.score(
				unit("교과", List.of(
						cutYear(2026, 1.6, 3.0), cutYear(2025, 1.6, 3.0), cutYear(2024, 1.6, 3.0))),
				RECENT_YEARS, 5.0, 3.0);

		// 1년: σ = 0.61 + 0.15 × 0.2 → LOW
		assertTrue(oneYear.lowConfidence());
		// 2년, 폭 0.2: σ = 0.44 + 0.03 = 0.47 → MEDIUM
		assertFalse(twoYearsNarrow.lowConfidence());
		// 2년, 폭 1.4: σ = 0.44 + 0.21 = 0.65 → LOW
		assertTrue(twoYearsWide.lowConfidence());
		// 3년, 폭 1.4: σ = 0.35 + 0.21 = 0.56 → MEDIUM
		assertFalse(threeYearsWide.lowConfidence());
	}

	@Test
	void evidenceUsesLatestNonNullValues() {
		CandidateUnit unit = unit("교과", List.of(
				new UnitYearData(2027, 15, null, null, null, null),
				new UnitYearData(2026, 20, 4.55, false, 2.2, 2.5),
				new UnitYearData(2025, 18, 5.0, true, 2.1, 2.4)
		));

		ScoredCandidate scored = scorer.score(unit, RECENT_YEARS, 4.0, 2.5);

		assertEquals(Integer.valueOf(15), scored.evidence().quota());
		assertEquals(Double.valueOf(4.55), scored.evidence().competitionRate());
		assertEquals(Boolean.FALSE, scored.evidence().newOrMergedDepartment());
	}

	@Test
	void missingEvidenceStaysNull() {
		CandidateUnit unit = unit("교과", List.of(new UnitYearData(2026, null, null, null, 2.2, 2.5)));

		ScoredCandidate scored = scorer.score(unit, RECENT_YEARS, 4.0, 2.5);

		assertNull(scored.evidence().quota());
		assertNull(scored.evidence().competitionRate());
		assertNull(scored.evidence().newOrMergedDepartment());
	}

	private CandidateUnit unit(String trackType, List<UnitYearData> years) {
		return new CandidateUnit(
				1L, 10L, "테스트대학", "테스트학과", trackType, "학생부교과(테스트전형)",
				CandidateKind.DESIRED_MAJOR, years);
	}

	private UnitYearData cutYear(int year, double cut50, double cut70) {
		return new UnitYearData(year, null, null, null, cut50, cut70);
	}

}
