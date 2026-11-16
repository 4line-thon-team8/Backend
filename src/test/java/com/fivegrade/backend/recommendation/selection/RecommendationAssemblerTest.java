package com.fivegrade.backend.recommendation.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.holistic.scoring.DocumentAdjustment;
import com.fivegrade.backend.holistic.scoring.DocumentSignal;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.DocumentFit;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 선택 결과를 추천 결과(카드와 안내)로 조립하는 규칙을 검증한다. DB나 Spring 없이 실행한다.
 */
class RecommendationAssemblerTest {

	private final RecommendationAssembler assembler = new RecommendationAssembler();

	private long nextId = 1;

	@Test
	void returnsEmptyResultWithNoCandidatesWarning() {
		RecommendationResult result = assembler.assemble(List.of(), false);

		assertTrue(result.cards().isEmpty());
		assertEquals(List.of(RecommendationWarning.forResult(WarningCode.NO_CANDIDATES)), result.warnings());
	}

	@Test
	void numbersCardsFromOneInGivenOrder() {
		List<ScoredCandidate> selected = sixDesiredCards();

		RecommendationResult result = assembler.assemble(selected, false);

		assertEquals(6, result.cards().size());
		for (int i = 0; i < 6; i++) {
			assertEquals(i + 1, result.cards().get(i).rank());
			assertEquals(selected.get(i).unitId(), result.cards().get(i).unitId());
		}
		assertTrue(result.warnings().isEmpty());
	}

	@Test
	void warnsWhenFewerThanSixCards() {
		RecommendationResult result = assembler.assemble(sixDesiredCards().subList(0, 3), false);

		assertEquals(List.of(RecommendationWarning.forResult(WarningCode.FEWER_THAN_SIX_CARDS)), result.warnings());
	}

	@Test
	void warnsWhenReachCardWasRequestedButMissing() {
		List<ScoredCandidate> selected = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			selected.add(candidate(CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, false));
		}

		assertTrue(assembler.assemble(selected, false).warnings().isEmpty());
		assertEquals(
				List.of(RecommendationWarning.forResult(WarningCode.REACH_CARD_NOT_AVAILABLE)),
				assembler.assemble(selected, true).warnings());
	}

	@Test
	void addsCardWarningsForExpandedUnratedAndLowConfidenceCards() {
		List<ScoredCandidate> selected = List.of(
				candidate(CandidateKind.SIMILAR_MAJOR, AdmissionBand.REACH, false),
				candidate(CandidateKind.OTHER_TRACK, AdmissionBand.MATCH, true),
				candidate(CandidateKind.UNDECLARED, AdmissionBand.SAFE, false),
				candidate(CandidateKind.DESIRED_MAJOR, AdmissionBand.UNRATED, true)
		);

		RecommendationResult result = assembler.assemble(selected, false);

		assertEquals(
				List.of(
						RecommendationWarning.forResult(WarningCode.FEWER_THAN_SIX_CARDS),
						RecommendationWarning.forCard(WarningCode.SIMILAR_MAJOR_USED, 1),
						RecommendationWarning.forCard(WarningCode.OTHER_TRACK_USED, 2),
						RecommendationWarning.forCard(WarningCode.LOW_CONFIDENCE_CARD, 2),
						RecommendationWarning.forCard(WarningCode.UNDECLARED_USED, 3),
						RecommendationWarning.forCard(WarningCode.UNRATED_CARD, 4)),
				result.warnings());
	}

	@Test
	void unratedCardHasNoGradeGap() {
		RecommendationResult result = assembler.assemble(
				List.of(candidate(CandidateKind.DESIRED_MAJOR, AdmissionBand.UNRATED, false)), false);

		assertNull(result.cards().get(0).gradeGap());
	}

	@Test
	void 서류_보정을_적용한_카드는_보정_전_구간과_서류_적합도를_함께_담는다() {
		DocumentAdjustment adjustment = new DocumentAdjustment(
				true, 0.4, AdmissionBand.REACH, AdmissionBand.MATCH, 0.5, DocumentAdjustment.Reason.APPLIED);
		ScoredCandidate base = candidate(CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, false);
		ScoredCandidate adjusted = new ScoredCandidate(
				base.unitId(), base.universityId(), base.universityName(), base.departmentName(),
				base.admissionTypeName(), base.kind(), base.band(), base.probability(), base.gradeGap(),
				base.evidence(), false, null, 0.7, adjustment,
				new DocumentSignal(4.2, 0.7, List.of("ACADEMIC", "MAJOR_FIT")));

		RecommendedCard card = assembler.assemble(List.of(adjusted), false).cards().getFirst();

		assertEquals(AdmissionBand.MATCH, card.band());
		assertEquals(AdmissionBand.REACH, card.baseBand());
		assertEquals(DocumentFit.Level.HIGH, card.documentFit().level());
		assertEquals(List.of("ACADEMIC", "MAJOR_FIT"), card.documentFit().basis());
	}

	@Test
	void 서류_보정을_하지_않은_카드는_보정_전_구간이_없고_입력이_있으면_적합도만_담는다() {
		DocumentAdjustment notApplied = DocumentAdjustment.none(
				AdmissionBand.FAR, null, DocumentAdjustment.Reason.BAND_NOT_ADJUSTABLE);
		ScoredCandidate base = candidate(CandidateKind.DESIRED_MAJOR, AdmissionBand.FAR, false);
		ScoredCandidate withSignal = new ScoredCandidate(
				base.unitId(), base.universityId(), base.universityName(), base.departmentName(),
				base.admissionTypeName(), base.kind(), base.band(), base.probability(), base.gradeGap(),
				base.evidence(), false, null, 0.7, notApplied, new DocumentSignal(1.5, 0.7));

		RecommendedCard plain = assembler.assemble(
				List.of(candidate(CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, false)), false).cards().getFirst();
		RecommendedCard card = assembler.assemble(List.of(withSignal), false).cards().getFirst();

		assertNull(plain.baseBand());
		assertNull(plain.documentFit());
		assertNull(card.baseBand());
		assertEquals(DocumentFit.Level.LOW, card.documentFit().level());
	}

	private List<ScoredCandidate> sixDesiredCards() {
		List<ScoredCandidate> cards = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			cards.add(candidate(CandidateKind.DESIRED_MAJOR, AdmissionBand.MATCH, false));
		}
		return cards;
	}

	private ScoredCandidate candidate(CandidateKind kind, AdmissionBand band, boolean lowConfidence) {
		long id = nextId++;
		boolean unrated = band == AdmissionBand.UNRATED;
		return new ScoredCandidate(
				id,
				id,
				"대학" + id,
				"학과" + id,
				"학생부교과(테스트전형)",
				kind,
				band,
				unrated ? null : 0.5,
				unrated ? null : 0.3,
				new CardEvidence(10, 3.5, false, unrated ? 0 : 3),
				lowConfidence
		);
	}

}
