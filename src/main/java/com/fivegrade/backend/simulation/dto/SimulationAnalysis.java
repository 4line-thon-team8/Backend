package com.fivegrade.backend.simulation.dto;

import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import java.math.BigDecimal;
import java.util.List;

/**
 * 최종 응답 조립 전 내부 분석 결과.
 * 외부 API 응답 DTO로 직접 반환하지 않는다.
 */
public record SimulationAnalysis(
        BigDecimal displayAverage,
        List<SubjectGrade> subjects,
        RecommendationResult recommendation
) {

    public SimulationAnalysis {
        subjects = List.copyOf(subjects);
    }

    /**
     * 사용자 5등급 입력을 그대로 유지한 과목별 표시 자료.
     */
    public record SubjectGrade(
            String subjectRef,
            String subjectCode,
            BigDecimal grade
    ) {
    }
}
