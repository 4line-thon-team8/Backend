package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.recommendation.dto.RecommendationQuery;
import com.fivegrade.backend.simulation.dto.ApplicationPreference;
import java.math.BigDecimal;

/**
 * 사용자 평균과 지원 방향을 추천 입력으로 매핑한다.
 */
@FunctionalInterface
public interface RecommendationQueryMapper {

    RecommendationQuery map(
            BigDecimal fiveGradeAverage,
            ApplicationPreference preference
    );
}
