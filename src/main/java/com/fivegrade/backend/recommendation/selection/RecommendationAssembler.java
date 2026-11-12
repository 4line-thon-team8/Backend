package com.fivegrade.backend.recommendation.selection;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import java.util.ArrayList;
import java.util.List;

/**
 * 고른 후보를 추천 결과(카드와 안내)로 조립한다.
 *
 * 안내는 코드만 담는다. 결과 전체에 대한 안내를 먼저, 카드별 안내를 지망 순서대로 이어 붙인다.
 * 후보가 하나도 없으면 {@link RecommendationResult#empty()}를 돌려준다.
 * 가능성(확률)과 모델 내부 계산값은 응답에 담지 않는다.
 *
 * Spring과 DB에 의존하지 않는 순수 Java 클래스다.
 */
public final class RecommendationAssembler {

	/**
	 * @param selected         지망 순서대로 정렬된 선택 결과
	 * @param includeReachCard 상향 카드를 요청했는지 여부
	 */
	public RecommendationResult assemble(List<ScoredCandidate> selected, boolean includeReachCard) {
		if (selected.isEmpty()) {
			return RecommendationResult.empty();
		}

		List<RecommendedCard> cards = new ArrayList<>();
		for (int i = 0; i < selected.size(); i++) {
			cards.add(toCard(i + 1, selected.get(i)));
		}

		List<RecommendationWarning> warnings = new ArrayList<>();
		if (cards.size() < RecommendationResult.MAX_CARDS) {
			warnings.add(RecommendationWarning.forResult(WarningCode.FEWER_THAN_SIX_CARDS));
		}
		if (includeReachCard && selected.stream().noneMatch(card -> card.band() == AdmissionBand.REACH)) {
			warnings.add(RecommendationWarning.forResult(WarningCode.REACH_CARD_NOT_AVAILABLE));
		}
		for (int i = 0; i < selected.size(); i++) {
			addCardWarnings(warnings, i + 1, selected.get(i));
		}

		return new RecommendationResult(cards, warnings);
	}

	private RecommendedCard toCard(int rank, ScoredCandidate candidate) {
		return new RecommendedCard(
				rank,
				candidate.unitId(),
				candidate.universityName(),
				candidate.departmentName(),
				candidate.admissionTypeName(),
				candidate.band(),
				candidate.kind(),
				candidate.gradeGap(),
				candidate.evidence()
		);
	}

	private void addCardWarnings(List<RecommendationWarning> warnings, int rank, ScoredCandidate candidate) {
		WarningCode kindWarning = warningOf(candidate.kind());
		if (kindWarning != null) {
			warnings.add(RecommendationWarning.forCard(kindWarning, rank));
		}
		if (candidate.band() == AdmissionBand.UNRATED) {
			warnings.add(RecommendationWarning.forCard(WarningCode.UNRATED_CARD, rank));
		} else if (candidate.lowConfidence()) {
			warnings.add(RecommendationWarning.forCard(WarningCode.LOW_CONFIDENCE_CARD, rank));
		}
	}

	private WarningCode warningOf(CandidateKind kind) {
		return switch (kind) {
			case DESIRED_MAJOR -> null;
			case SIMILAR_MAJOR -> WarningCode.SIMILAR_MAJOR_USED;
			case OTHER_TRACK -> WarningCode.OTHER_TRACK_USED;
			case UNDECLARED -> WarningCode.UNDECLARED_USED;
		};
	}

}
