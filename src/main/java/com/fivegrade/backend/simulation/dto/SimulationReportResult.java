package com.fivegrade.backend.simulation.dto;

import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.report.generation.ConsultingReport;
import com.fivegrade.backend.report.generation.ReportSection;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 설명까지 조립한 내부 결과. 공개 응답 계약 확정 전 Controller에서 직접 반환하지 않는다.
 * 저장 시에도 이 객체 전체를 직렬화하지 않고 최소 저장 계약으로 별도 매핑한다.
 */
public record SimulationReportResult(
        BigDecimal displayAverage,
        List<SimulationAnalysis.SubjectGrade> subjects,
        List<ExplainedCard> recommendations,
        List<RecommendationWarning> warnings,
        ConsultingReport report
) {

    public SimulationReportResult {
        Objects.requireNonNull(displayAverage);
        subjects = List.copyOf(subjects);
        recommendations = List.copyOf(recommendations);
        warnings = List.copyOf(warnings);
        Objects.requireNonNull(report);
    }

    /** 원본 카드의 값과 순서를 유지하며 이유만 연결한다. */
    public record ExplainedCard(RecommendedCard card, ReportSection reason) {
        public ExplainedCard {
            Objects.requireNonNull(card);
            Objects.requireNonNull(reason);
        }
    }
}
