package com.fivegrade.backend.recommendation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 추천 서비스 출력의 불변 조건(장수, 지망 순서, 중복, 안내 연결)을 확인한다.
 *
 * DB나 Spring 없이 실행한다.
 */
class RecommendationResultTest {

	private static final CardEvidence EVIDENCE = new CardEvidence(10, 5.5, false, 3);

	private static RecommendedCard card(int rank, String university) {
		return new RecommendedCard(rank, rank, university, "국어국문학과", "교과", AdmissionBand.MATCH,
				CandidateKind.DESIRED_MAJOR, 0.3, EVIDENCE);
	}

	private static List<RecommendedCard> cards(int count) {
		List<RecommendedCard> cards = new ArrayList<>();
		for (int rank = 1; rank <= count; rank++) {
			cards.add(card(rank, "대학" + rank));
		}
		return cards;
	}

	@Test
	void acceptsUpToSixCardsInRankOrder() {
		RecommendationResult result = new RecommendationResult(cards(6), List.of());

		assertEquals(6, result.cards().size());
		assertTrue(result.warnings().isEmpty());
	}

	@Test
	void acceptsFewerThanSixCards() {
		RecommendationResult result = new RecommendationResult(
				cards(4), List.of(RecommendationWarning.forResult(WarningCode.FEWER_THAN_SIX_CARDS)));

		assertEquals(4, result.cards().size());
	}

	@Test
	void providesAnEmptyResultWithAWarning() {
		RecommendationResult result = RecommendationResult.empty();

		assertTrue(result.cards().isEmpty());
		assertEquals(WarningCode.NO_CANDIDATES, result.warnings().get(0).code());
		assertNull(result.warnings().get(0).rank());
	}

	@Test
	void rejectsMoreThanSixCards() {
		assertThrows(IllegalArgumentException.class, () -> new RecommendationResult(cards(7), List.of()));
	}

	@Test
	void rejectsCardsThatAreNotInRankOrder() {
		List<RecommendedCard> shuffled = new ArrayList<>(cards(3));
		shuffled.add(0, shuffled.remove(1));

		assertThrows(IllegalArgumentException.class, () -> new RecommendationResult(shuffled, List.of()));
	}

	@Test
	void rejectsTheSameTargetTwice() {
		List<RecommendedCard> duplicated = List.of(card(1, "대학A"), card(2, "대학A"));

		assertThrows(IllegalArgumentException.class, () -> new RecommendationResult(duplicated, List.of()));
	}

	@Test
	void rejectsAWarningForACardThatDoesNotExist() {
		List<RecommendationWarning> warnings = List.of(RecommendationWarning.forCard(WarningCode.UNRATED_CARD, 3));

		assertThrows(IllegalArgumentException.class, () -> new RecommendationResult(cards(2), warnings));
	}

	@Test
	void keepsCardWarningsWithTheirRank() {
		RecommendationResult result = new RecommendationResult(
				cards(2), List.of(RecommendationWarning.forCard(WarningCode.SIMILAR_MAJOR_USED, 2)));

		assertEquals(Integer.valueOf(2), result.warnings().get(0).rank());
	}

	@Test
	void rejectsInvalidCardValues() {
		assertThrows(IllegalArgumentException.class, () -> card(0, "대학A"));
		assertThrows(IllegalArgumentException.class, () -> card(7, "대학A"));
		assertThrows(IllegalArgumentException.class, () -> card(1, " "));
		assertThrows(IllegalArgumentException.class, () -> new RecommendedCard(
				1, 1, "대학A", "학과", "교과", AdmissionBand.UNRATED, CandidateKind.DESIRED_MAJOR, 0.3, EVIDENCE));
	}

	@Test
	void allowsAnUnratedCardWithoutGradeGap() {
		RecommendedCard unrated = new RecommendedCard(
				1, 1, "대학A", "학과", "교과", AdmissionBand.UNRATED, CandidateKind.DESIRED_MAJOR, null,
				new CardEvidence(null, null, null, 0));

		assertNull(unrated.gradeGap());
	}

	@Test
	void rejectsNegativeEvidenceValues() {
		assertThrows(IllegalArgumentException.class, () -> new CardEvidence(-1, null, null, 0));
		assertThrows(IllegalArgumentException.class, () -> new CardEvidence(null, -0.1, null, 0));
		assertThrows(IllegalArgumentException.class, () -> new CardEvidence(null, null, null, -1));
	}

}
