package com.fivegrade.backend.recommendation.selection;

import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.recommendation.probability.TrackKind;
import java.util.List;

/**
 * 서류 수준에 맞춰 학종 카드 장수를 제한하고, 안정 카드 한 장은 서류에 기대지 않게 하는 정책.
 *
 * 1. 학종 카드는 서류 수준별 상한({@link TrackMixParameters#holisticCap})을 넘기지 않는다.
 * 2. 안정(SAFE) 카드 2장이 모두 서류 의존 전형이 되지 않게 한다. 안전판은 교과 전형이거나 서류 비중이 낮은 전형이다.
 *
 * 서류 수준을 모르면(null) 제한하지 않는다. 안전판 규칙은 서류 수준과 무관하게 항상 적용하지 않고,
 * 서류 반영을 한 요청에서만 쓰도록 이 정책을 서류 수준이 있을 때만 만든다.
 */
public final class HolisticTrackMixPolicy implements TrackMixPolicy {

	private final int holisticCap;
	private final TrackMixParameters parameters;

	/** @param documentLevel 사용자의 서류 수준(1~5) */
	public HolisticTrackMixPolicy(double documentLevel) {
		this(documentLevel, TrackMixParameters.DEFAULT);
	}

	public HolisticTrackMixPolicy(double documentLevel, TrackMixParameters parameters) {
		this.parameters = parameters;
		this.holisticCap = parameters.holisticCap(documentLevel);
	}

	@Override
	public boolean allows(List<ScoredCandidate> picked, ScoredCandidate next) {
		if (next.trackKind() == TrackKind.HOLISTIC && countHolistic(picked) >= holisticCap) {
			return false;
		}
		if (next.band() == AdmissionBand.SAFE && !isAnchor(next)) {
			boolean hasSafe = false;
			for (ScoredCandidate card : picked) {
				if (card.band() == AdmissionBand.SAFE) {
					hasSafe = true;
					if (isAnchor(card)) {
						return true;
					}
				}
			}
			// 이미 안정 카드가 있는데 모두 서류 의존이면, 두 번째 서류 의존 안정 카드는 막는다.
			return !hasSafe;
		}
		return true;
	}

	private int countHolistic(List<ScoredCandidate> picked) {
		int count = 0;
		for (ScoredCandidate card : picked) {
			if (card.trackKind() == TrackKind.HOLISTIC) {
				count++;
			}
		}
		return count;
	}

	/** 서류에 기대지 않는 안전판인지: 교과 전형이거나, 서류 비중이 기준보다 낮은 전형 */
	private boolean isAnchor(ScoredCandidate card) {
		if (card.trackKind() == TrackKind.SUBJECT) {
			return card.documentShare() == null || card.documentShare() < parameters.anchorMaxShare();
		}
		return card.documentShare() != null && card.documentShare() < parameters.anchorMaxShare();
	}

}
