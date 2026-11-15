package com.fivegrade.backend.report.generation;

import java.util.Objects;

/** 노션의 컨설팅 리포트 4개 섹션을 담는다. */
public record ConsultingReport(
        ReportSection greetingSummary,
        ReportSection overallAssessment,
        ReportSection applicationStrategy,
        ReportSection closing
) {

    public ConsultingReport {
        Objects.requireNonNull(greetingSummary);
        Objects.requireNonNull(overallAssessment);
        Objects.requireNonNull(applicationStrategy);
        Objects.requireNonNull(closing);
    }
}
