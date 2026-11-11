package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.recommendation.dto.RecommendationQuery;
import com.fivegrade.backend.recommendation.dto.TrackPreference;
import com.fivegrade.backend.simulation.dto.ApplicationPreference;
import com.fivegrade.backend.simulation.dto.PreferredTrack;
import java.math.BigDecimal;
import java.util.List;

/**
 * 분석 요청의 지원 방향을 추천 서비스 계약으로 매핑한다.
 * 요청과 추천의 전형 enum을 분리하고 5등급 평균은 변환 없이 전달한다.
 */
public final class DefaultRecommendationQueryMapper
		implements RecommendationQueryMapper {

	@Override
	public RecommendationQuery map(
			BigDecimal fiveGradeAverage,
			ApplicationPreference preference
	) {
		return new RecommendationQuery(
				fiveGradeAverage,
				List.copyOf(preference.desiredMajorCodes()),
				toTrackPreference(preference.preferredTrack()),
				preference.includeReachCard(),
				preference.allowSimilarMajors()
		);
	}

	private TrackPreference toTrackPreference(
			PreferredTrack preferredTrack
	) {
		return switch (preferredTrack) {
			case SUBJECT -> TrackPreference.SUBJECT;
			case HOLISTIC -> TrackPreference.HOLISTIC;
			case ANY -> TrackPreference.ANY;
		};
	}
}