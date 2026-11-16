package com.fivegrade.backend.simulation.dto;

import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 최종 응답 조립 전 내부 분석 결과.
 * 외부 API 응답 DTO로 직접 반환하지 않는다.
 */
public record SimulationAnalysis(
        BigDecimal displayAverage,
        List<SubjectGrade> subjects,
        RecommendationResult recommendation,
        HolisticEvaluation holistic
) {

    public SimulationAnalysis {
        subjects = List.copyOf(subjects);
        Objects.requireNonNull(holistic, "holistic evaluation is required");
    }

    /** 서술 분석을 반영하지 않은(보내지 않은) 분석 결과 */
    public SimulationAnalysis(
            BigDecimal displayAverage,
            List<SubjectGrade> subjects,
            RecommendationResult recommendation
    ) {
        this(displayAverage, subjects, recommendation, new HolisticEvaluation.Skipped(
                HolisticEvaluation.SkipReason.NOT_PROVIDED));
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
