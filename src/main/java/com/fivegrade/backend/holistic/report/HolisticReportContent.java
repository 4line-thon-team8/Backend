package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.report.generation.ReportSection;
import java.util.List;
import java.util.Objects;

/** 생기부 분석 리포트의 글 부분. 문장마다 실제 생성 방식(template/llm)을 남긴다. */
public record HolisticReportContent(
		ReportSection summary,
		List<ReportSection> strengths,
		List<ReportSection> toDevelop,
		List<ReportSection> nextSteps,
		ReportSection caveat
) {

	public HolisticReportContent {
		Objects.requireNonNull(summary);
		Objects.requireNonNull(caveat);
		strengths = List.copyOf(strengths);
		toDevelop = List.copyOf(toDevelop);
		nextSteps = List.copyOf(nextSteps);
	}
}
