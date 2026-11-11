package com.fivegrade.backend.recommendation.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 6장 선택 규칙(구간별 목표, 대학당 제한, 희망 학과 우선, 보충 순서)을 검증한다. DB나 Spring 없이 실행한다.
 */
class CardSelectorTest {

	private final CardSelector selector = new CardSelector();

	private long nextId = 1;

	@Test
	void targetsTwoSafeTwoMatchTwoReach() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.SAFE, 0.90);
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.65);
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.REACH, 0.25);

		List<ScoredCandidate> selected = selector.select(candidates, false);

		assertEquals(6, selected.size());
		assertEquals(2, count(selected, AdmissionBand.SAFE));
		assertEquals(2, count(selected, AdmissionBand.MATCH));
		assertEquals(2, count(selected, AdmissionBand.REACH));
	}

	@Test
	void ranksReachFirstThenMatchThenSafe() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 2, CandidateKind.DESIRED_MAJOR, AdmissionBand.SAFE, 0.90);
		addMany(candidates, 2, CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.65);
		addMany(candidates, 2, CandidateKind.DESIRED_MAJOR, AdmissionBand.REACH, 0.25);

		List<ScoredCandidate> selected = selector.select(candidates, false);

		assertEquals(
				List.of(
						AdmissionBand.REACH, AdmissionBand.REACH,
						AdmissionBand.MATCH, AdmissionBand.MATCH,
						AdmissionBand.SAFE, AdmissionBand.SAFE),
				selected.stream().map(ScoredCandidate::band).toList());
	}

	@Test
	void allowsAtMostTwoCardsPerUniversity() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			candidates.add(candidate(7L, "같은대학", "학과" + i,
					CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.65));
		}

		List<ScoredCandidate> selected = selector.select(candidates, false);

		assertEquals(2, selected.size());
	}

	@Test
	void takesSameDepartmentOnlyOnceEvenWithDifferentTracks() {
		List<ScoredCandidate> candidates = List.of(
				candidate(7L, "대학", "같은학과", CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.65),
				candidate(7L, "대학", "같은학과", CandidateKind.DESIRED_MAJOR, AdmissionBand.SAFE, 0.90)
		);

		List<ScoredCandidate> selected = selector.select(candidates, false);

		assertEquals(1, selected.size());
	}

	@Test
	void usesDesiredMajorBeforeSimilarMajor() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 2, CandidateKind.DESIRED_MAJOR, AdmissionBand.SAFE, 0.90);
		addMany(candidates, 2, CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.65);
		addMany(candidates, 1, CandidateKind.DESIRED_MAJOR, AdmissionBand.REACH, 0.25);
		addMany(candidates, 3, CandidateKind.SIMILAR_MAJOR, AdmissionBand.REACH, 0.25);
		addMany(candidates, 3, CandidateKind.SIMILAR_MAJOR, AdmissionBand.SAFE, 0.90);

		List<ScoredCandidate> selected = selector.select(candidates, false);

		assertEquals(6, selected.size());
		assertEquals(5, countKind(selected, CandidateKind.DESIRED_MAJOR));
		assertEquals(1, countKind(selected, CandidateKind.SIMILAR_MAJOR));
		assertEquals(2, count(selected, AdmissionBand.REACH));
	}

	@Test
	void fillsRemainingSlotsWithMatchThenSafeThenReach() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.SAFE, 0.90);
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.65);

		List<ScoredCandidate> selected = selector.select(candidates, false);

		assertEquals(6, selected.size());
		assertEquals(3, count(selected, AdmissionBand.SAFE));
		assertEquals(3, count(selected, AdmissionBand.MATCH));
	}

	@Test
	void expandsToOtherTracksBeforeAcceptingFarCards() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 1, CandidateKind.DESIRED_MAJOR, AdmissionBand.SAFE, 0.90);
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.FAR, 0.01);
		addMany(candidates, 2, CandidateKind.OTHER_TRACK, AdmissionBand.MATCH, 0.65);

		List<ScoredCandidate> selected = selector.select(candidates, false);

		assertEquals(6, selected.size());
		assertEquals(2, countKind(selected, CandidateKind.OTHER_TRACK));
		assertEquals(3, count(selected, AdmissionBand.FAR));
		// 보충 카드는 지망 순서의 뒤쪽에 온다.
		assertEquals(AdmissionBand.MATCH, selected.get(0).band());
		assertEquals(AdmissionBand.FAR, selected.get(5).band());
	}

	@Test
	void usesOverBeforeFarBeforeUnrated() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 2, CandidateKind.DESIRED_MAJOR, AdmissionBand.UNRATED, null);
		addMany(candidates, 1, CandidateKind.DESIRED_MAJOR, AdmissionBand.FAR, 0.01);
		addMany(candidates, 1, CandidateKind.DESIRED_MAJOR, AdmissionBand.OVER, 0.999);

		List<ScoredCandidate> selected = selector.select(candidates, false);

		assertEquals(
				List.of(AdmissionBand.OVER, AdmissionBand.FAR, AdmissionBand.UNRATED, AdmissionBand.UNRATED),
				selected.stream().map(ScoredCandidate::band).toList());
	}

	@Test
	void returnsOnlyWhatExistsWhenCandidatesAreFewerThanSix() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 2, CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.65);

		assertEquals(2, selector.select(candidates, false).size());
		assertTrue(selector.select(List.of(), false).isEmpty());
	}

	@Test
	void picksCandidatesClosestToThePreferredProbabilityInABand() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		candidates.add(candidate(1L, "가대학", "가학과", CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.46));
		candidates.add(candidate(2L, "나대학", "나학과", CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.66));
		candidates.add(candidate(3L, "다대학", "다학과", CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.80));

		List<ScoredCandidate> selected = selector.select(candidates, false);

		assertEquals(3, selected.size());
		// 구간별 목표(2장)를 넘는 나머지는 보충 단계에서 담겨 모두 선택되지만, 먼저 고르는 쪽은 0.66, 0.80 이다.
		assertEquals(0.66, selected.get(0).probability());
		assertEquals(0.80, selected.get(1).probability());
	}

	@Test
	void forcesOneReachCardWhenRequestedAndAvailable() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.SAFE, 0.90);
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.65);
		addMany(candidates, 1, CandidateKind.SIMILAR_MAJOR, AdmissionBand.REACH, 0.25);

		List<ScoredCandidate> withoutRequest = selector.select(candidates, false);
		List<ScoredCandidate> withRequest = selector.select(candidates, true);

		assertEquals(0, count(withoutRequest, AdmissionBand.REACH));
		assertEquals(6, withRequest.size());
		assertEquals(1, count(withRequest, AdmissionBand.REACH));
		assertEquals(AdmissionBand.REACH, withRequest.get(0).band());
	}

	@Test
	void keepsSelectionWhenNoReachCandidateExists() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.SAFE, 0.90);
		addMany(candidates, 3, CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, 0.65);

		List<ScoredCandidate> selected = selector.select(candidates, true);

		assertEquals(6, selected.size());
		assertEquals(0, count(selected, AdmissionBand.REACH));
	}

	@Test
	void neverExceedsSixCardsOrRepeatsAUnit() {
		List<ScoredCandidate> candidates = new ArrayList<>();
		for (CandidateKind kind : CandidateKind.values()) {
			addMany(candidates, 10, kind, AdmissionBand.SAFE, 0.90);
			addMany(candidates, 10, kind, AdmissionBand.MATCH, 0.65);
			addMany(candidates, 10, kind, AdmissionBand.REACH, 0.25);
		}

		List<ScoredCandidate> selected = selector.select(candidates, true);

		Set<Long> unitIds = new HashSet<>();
		for (ScoredCandidate card : selected) {
			assertTrue(unitIds.add(card.unitId()));
		}
		assertEquals(6, selected.size());
	}

	private void addMany(
			List<ScoredCandidate> target,
			int count,
			CandidateKind kind,
			AdmissionBand band,
			Double probability
	) {
		for (int i = 0; i < count; i++) {
			long id = nextId++;
			target.add(candidate(id, "대학" + id, "학과" + id, kind, band, probability));
		}
	}

	private ScoredCandidate candidate(
			long universityId,
			String universityName,
			String departmentName,
			CandidateKind kind,
			AdmissionBand band,
			Double probability
	) {
		long unitId = nextId++;
		return new ScoredCandidate(
				unitId,
				universityId,
				universityName,
				departmentName,
				"학생부교과(테스트전형)",
				kind,
				band,
				probability,
				band == AdmissionBand.UNRATED ? null : 0.5,
				new CardEvidence(null, null, null, 3),
				false
		);
	}

	private int count(List<ScoredCandidate> cards, AdmissionBand band) {
		return (int) cards.stream().filter(card -> card.band() == band).count();
	}

	private int countKind(List<ScoredCandidate> cards, CandidateKind kind) {
		return (int) cards.stream().filter(card -> card.kind() == kind).count();
	}

}
