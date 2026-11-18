package com.fivegrade.backend.recommendation.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.holistic.scoring.DocumentSignal;
import com.fivegrade.backend.recommendation.candidate.CandidateUnit;
import com.fivegrade.backend.recommendation.candidate.UnitYearData;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.probability.TrackKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 서류 신호를 넘겼을 때와 넘기지 않았을 때의 점수 계산을 비교한다. DB나 Spring 없이 실행한다.
 */
class CandidateScorerDocumentTest {

	private static final List<Integer> RECENT_YEARS = List.of(2026, 2025, 2024);

	private final CandidateScorer scorer = new CandidateScorer();

	@Test
	void withoutSignalResultEqualsLegacyScoringAndHasNoAdjustment() {
		CandidateUnit unit = holisticUnit();

		ScoredCandidate legacy = scorer.score(unit, RECENT_YEARS, 3.3, 2.5);
		ScoredCandidate explicitNull = scorer.score(unit, RECENT_YEARS, 3.3, 2.5, null);

		assertEquals(legacy, explicitNull);
		assertNull(legacy.documentAdjustment());
		assertEquals(TrackKind.HOLISTIC, legacy.trackKind());
	}

	@Test
	void highDocumentLevelRaisesProbabilityOfHolisticCandidate() {
		CandidateUnit unit = holisticUnit();

		ScoredCandidate base = scorer.score(unit, RECENT_YEARS, 3.3, 2.5);
		ScoredCandidate adjusted = scorer.score(unit, RECENT_YEARS, 3.3, 2.5, new DocumentSignal(5.0, 1.0));

		assertNotNull(adjusted.documentAdjustment());
		assertTrue(adjusted.probability() > base.probability());
		assertEquals(1.0, adjusted.documentShare());
		// 원본 gradeGap은 서류와 무관하게 그대로다.
		assertEquals(base.gradeGap(), adjusted.gradeGap());
	}

	@Test
	void displayOnlySignalKeepsBandAndProbabilityButKeepsFitForDisplay() {
		CandidateUnit unit = holisticUnit();
		DocumentSignal display = DocumentSignal.displayOnly(5.0, List.of("ACADEMIC"));

		ScoredCandidate base = scorer.score(unit, RECENT_YEARS, 3.3, 2.5);
		ScoredCandidate shown = scorer.score(unit, RECENT_YEARS, 3.3, 2.5, display);

		assertNull(shown.documentAdjustment());
		assertEquals(base.band(), shown.band());
		assertEquals(base.probability(), shown.probability());
		assertEquals(display, shown.documentSignal());
	}

	private CandidateUnit holisticUnit() {
		return new CandidateUnit(
				1L, 10L, "테스트대학", "테스트학과", "학종", "학생부종합(테스트전형)",
				CandidateKind.DESIRED_MAJOR,
				List.of(
						new UnitYearData(2026, null, null, null, 2.6, 3.2),
						new UnitYearData(2025, null, null, null, 2.6, 3.2),
						new UnitYearData(2024, null, null, null, 2.6, 3.2)));
	}

}
