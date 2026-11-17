package com.fivegrade.backend.recommendation.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.recommendation.probability.TrackKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 전형 구성 제한(학종 장수 상한, 안전판)과 제한 완화를 검증한다. DB나 Spring 없이 실행한다.
 */
class CardSelectorTrackMixTest {

	private long nextId = 1;
	private long nextUniversity = 100;

	@Test
	void noopPolicyGivesSameResultAsDefaultSelector() {
		List<ScoredCandidate> candidates = mixedPool();

		List<ScoredCandidate> byDefault = new CardSelector().select(candidates, false);
		List<ScoredCandidate> byNoop = new CardSelector(NoopTrackMixPolicy.INSTANCE).select(candidates, false);

		assertEquals(byDefault, byNoop);
	}

	@Test
	void holisticCapLimitsHolisticCards() {
		// 서류 수준 2.5(낮음) → 학종 상한 2. 학종 후보가 많아도 2장까지만 담고 교과가 채운다.
		List<ScoredCandidate> candidates = mixedPool();

		CardSelector.Outcome outcome = new CardSelector(new HolisticTrackMixPolicy(2.5))
				.selectWithOutcome(candidates, false);

		assertEquals(6, outcome.cards().size());
		assertTrue(countKind(outcome.cards(), TrackKind.HOLISTIC) <= 2);
		assertTrue(outcome.relaxedUnitIds().isEmpty());
	}

	@Test
	void highDocumentLevelAllowsManyHolisticCards() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 8, TrackKind.HOLISTIC, AdmissionBand.MATCH, 0.65, 1.0);

		List<ScoredCandidate> selected = new CardSelector(new HolisticTrackMixPolicy(4.5)).select(candidates, false);

		assertEquals(6, countKind(selected, TrackKind.HOLISTIC));
	}

	@Test
	void capIsRelaxedOnlyWhenSixCardsCannotBeFilled() {
		// 학종만 있는 풀. 상한 1이어도 6장을 채우려면 제한을 풀어야 한다.
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 6, TrackKind.HOLISTIC, AdmissionBand.MATCH, 0.65, 1.0);

		CardSelector.Outcome outcome = new CardSelector(new HolisticTrackMixPolicy(1.5))
				.selectWithOutcome(candidates, false);

		assertEquals(6, outcome.cards().size());
		assertEquals(5, outcome.relaxedUnitIds().size());
	}

	@Test
	void secondSafeCardMustBeAnchorWhenFirstDependsOnDocument() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 3, TrackKind.HOLISTIC, AdmissionBand.SAFE, 0.90, 1.0);
		candidates.add(candidate(TrackKind.SUBJECT, AdmissionBand.SAFE, 0.86, null));
		addMany(candidates, 4, TrackKind.SUBJECT, AdmissionBand.MATCH, 0.65, null);

		CardSelector.Outcome outcome = new CardSelector(new HolisticTrackMixPolicy(4.5))
				.selectWithOutcome(candidates, false);

		List<ScoredCandidate> safeCards = outcome.cards().stream()
				.filter(card -> card.band() == AdmissionBand.SAFE).toList();
		assertEquals(6, outcome.cards().size());
		assertEquals(2, safeCards.size());
		assertEquals(1, countKind(safeCards, TrackKind.SUBJECT));
		assertTrue(outcome.relaxedUnitIds().isEmpty());
	}

	@Test
	void lowDocumentShareHolisticTrackCountsAsAnchor() {
		HolisticTrackMixPolicy policy = new HolisticTrackMixPolicy(4.5);
		ScoredCandidate firstSafe = candidate(TrackKind.HOLISTIC, AdmissionBand.SAFE, 0.9, 1.0);
		ScoredCandidate lowShare = candidate(TrackKind.HOLISTIC, AdmissionBand.SAFE, 0.9, 0.3);
		ScoredCandidate highShare = candidate(TrackKind.HOLISTIC, AdmissionBand.SAFE, 0.9, 1.0);

		assertTrue(policy.allows(List.of(firstSafe), lowShare));
		assertFalse(policy.allows(List.of(firstSafe), highShare));
	}

	@Test
	void safeAnchorRuleDoesNotApplyToMatchOrReachCards() {
		HolisticTrackMixPolicy policy = new HolisticTrackMixPolicy(4.5);
		ScoredCandidate firstSafe = candidate(TrackKind.HOLISTIC, AdmissionBand.SAFE, 0.9, 1.0);

		assertTrue(policy.allows(List.of(firstSafe), candidate(TrackKind.HOLISTIC, AdmissionBand.MATCH, 0.65, 1.0)));
		assertTrue(policy.allows(List.of(firstSafe), candidate(TrackKind.HOLISTIC, AdmissionBand.REACH, 0.25, 1.0)));
	}

	@Test
	void capTableFollowsLevelBoundaries() {
		TrackMixParameters p = TrackMixParameters.DEFAULT;

		assertEquals(6, p.holisticCap(4.2));
		assertEquals(4, p.holisticCap(3.6));
		assertEquals(3, p.holisticCap(2.8));
		assertEquals(2, p.holisticCap(2.0));
		assertEquals(1, p.holisticCap(1.9));
	}

	private List<ScoredCandidate> mixedPool() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 4, TrackKind.HOLISTIC, AdmissionBand.MATCH, 0.65, 1.0);
		addMany(candidates, 2, TrackKind.HOLISTIC, AdmissionBand.REACH, 0.25, 1.0);
		addMany(candidates, 3, TrackKind.SUBJECT, AdmissionBand.SAFE, 0.90, null);
		addMany(candidates, 3, TrackKind.SUBJECT, AdmissionBand.MATCH, 0.65, null);
		addMany(candidates, 2, TrackKind.SUBJECT, AdmissionBand.REACH, 0.25, null);
		return candidates;
	}

	private void addMany(List<ScoredCandidate> list, int count, TrackKind kind, AdmissionBand band,
			double probability, Double share) {
		for (int i = 0; i < count; i++) {
			list.add(candidate(kind, band, probability, share));
		}
	}

	private int countKind(List<ScoredCandidate> cards, TrackKind kind) {
		int count = 0;
		for (ScoredCandidate card : cards) {
			if (card.trackKind() == kind) {
				count++;
			}
		}
		return count;
	}

	private ScoredCandidate candidate(TrackKind kind, AdmissionBand band, double probability, Double share) {
		long unitId = nextId++;
		long universityId = nextUniversity++;
		return new ScoredCandidate(
				unitId, universityId, "대학" + universityId, "학과" + unitId, "전형",
				CandidateKind.DESIRED_MAJOR, band, probability, 0.0,
				new CardEvidence(null, null, null, 3), false,
				kind, share, null);
	}

}
