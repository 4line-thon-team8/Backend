package com.fivegrade.backend.recommendation.selection;

import java.util.List;

/** 전형 구성 제한이 없는 기본 정책. 서류 반영을 하지 않을 때 쓰며, 기존 선택 결과와 똑같다. */
public final class NoopTrackMixPolicy implements TrackMixPolicy {

	public static final NoopTrackMixPolicy INSTANCE = new NoopTrackMixPolicy();

	private NoopTrackMixPolicy() {
	}

	@Override
	public boolean allows(List<ScoredCandidate> picked, ScoredCandidate next) {
		return true;
	}

}
