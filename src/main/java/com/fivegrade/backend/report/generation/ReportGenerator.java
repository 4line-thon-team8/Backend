package com.fivegrade.backend.report.generation;

import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** 확정된 평균과 추천 결과를 설명한다. 추천을 다시 계산하지 않는다. */
public interface ReportGenerator {

    GeneratedReport generate(
            BigDecimal displayAverage,
            RecommendationResult recommendation
    );

    /** reasons는 추천 카드와 동일한 순서로 생성한다. */
    record GeneratedReport(
            ConsultingReport report,
            List<CardReason> reasons
    ) {
        public GeneratedReport {
            Objects.requireNonNull(report);
            reasons = List.copyOf(reasons);
        }
    }

    record CardReason(int rank, ReportSection reason) {
        public CardReason {
            Objects.requireNonNull(reason);
        }
    }
}
