package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.conversion.calculation.FiveGradeAverageCalculator;
import com.fivegrade.backend.recommendation.dto.RecommendationQuery;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.service.RecommendationService;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis.SubjectGrade;
import com.fivegrade.backend.simulation.dto.SimulationRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 검증 완료된 요청의 평균 계산과 추천 호출을 연결한다.
 *
 * DTO, 동의, 허용 과목, 학과 존재 검증은 호출 전에 완료해야 한다.
 * 사용자 평균의 5→9 변환은 추천 서비스 내부에서 수행한다.
 */
public final class SimulationService {

    private final FiveGradeAverageCalculator averageCalculator;
    private final RecommendationQueryMapper queryMapper;
    private final RecommendationService recommendationService;

    public SimulationService(
            FiveGradeAverageCalculator averageCalculator,
            RecommendationQueryMapper queryMapper,
            RecommendationService recommendationService
    ) {
        this.averageCalculator = Objects.requireNonNull(averageCalculator);
        this.queryMapper = Objects.requireNonNull(queryMapper);
        this.recommendationService = Objects.requireNonNull(
                recommendationService
        );
    }

    /**
     * 내부 분석 결과를 반환한다.
     * 설명 생성과 외부 응답 조립은 다음 단계에서 수행한다.
     */
    public SimulationAnalysis analyzeValidated(
            SimulationRequest request
    ) {
        List<BigDecimal> grades = request.subjects().stream()
                .map(subject ->
                        BigDecimal.valueOf(subject.gradeValue()))
                .toList();

        BigDecimal comparisonAverage =
                averageCalculator.calculateForComparison(grades);

        BigDecimal displayAverage =
                averageCalculator.calculateForDisplay(grades);

        RecommendationQuery query = queryMapper.map(
                comparisonAverage,
                request.applicationPreference()
        );

        RecommendationResult result = Objects.requireNonNull(
                recommendationService.recommend(query),
                "recommendation result is required"
        );

        List<SubjectGrade> subjects = request.subjects().stream()
                .map(subject -> new SubjectGrade(
                        subject.subjectRef(),
                        subject.subjectCode(),
                        BigDecimal.valueOf(subject.gradeValue())
                                .setScale(2)
                ))
                .toList();

        return new SimulationAnalysis(
                displayAverage,
                subjects,
                result
        );
    }
}
