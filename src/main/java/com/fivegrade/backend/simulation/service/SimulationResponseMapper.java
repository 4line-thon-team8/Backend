package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.report.generation.ConsultingReport;
import com.fivegrade.backend.report.generation.ReportSection;
import com.fivegrade.backend.report.generation.TemplateReportGenerator;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import com.fivegrade.backend.simulation.dto.SimulationReportResult.ExplainedCard;
import com.fivegrade.backend.simulation.dto.SimulationResponse;
import com.fivegrade.backend.simulation.dto.SimulationResponse.Evidence;
import com.fivegrade.backend.simulation.dto.SimulationResponse.Explanation;
import com.fivegrade.backend.simulation.dto.SimulationResponse.RecommendationItem;
import com.fivegrade.backend.simulation.dto.SimulationResponse.ReportBody;
import com.fivegrade.backend.simulation.dto.SimulationResponse.SubjectResult;
import com.fivegrade.backend.simulation.dto.SimulationResponse.WarningItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 내부 결과(SimulationReportResult)를 공개 응답으로 바꾼다.
 *
 * 값을 새로 계산하지 않고 모양만 바꾼다. 유일한 가공은 gradeGap의 표시용 반올림이다.
 * 추천 서비스의 gradeGap은 반올림 전 값이므로 여기서 소수 둘째 자리로 한 번만 반올림한다.
 * 반올림한 값으로 구간을 다시 판단하거나 gradeGap을 다시 계산하지 않는다.
 */
public final class SimulationResponseMapper {

	private static final int GRADE_GAP_SCALE = 2;

	private SimulationResponseMapper() {
	}

	public static SimulationResponse toResponse(SimulationReportResult result) {
		Objects.requireNonNull(result);
		int cardCount = result.recommendations().size();

		List<SubjectResult> subjects = result.subjects().stream()
				.map(subject -> new SubjectResult(subject.subjectRef(), subject.subjectCode(), subject.grade()))
				.toList();
		List<RecommendationItem> recommendations = result.recommendations().stream()
				.map(SimulationResponseMapper::toItem)
				.toList();
		List<WarningItem> warnings = result.warnings().stream()
				.map(warning -> toWarning(warning, cardCount))
				.toList();

		return new SimulationResponse(
				result.displayAverage(),
				subjects,
				recommendations,
				warnings,
				toReport(result.report())
		);
	}

	private static RecommendationItem toItem(ExplainedCard explained) {
		RecommendedCard card = explained.card();
		return new RecommendationItem(
				card.rank(),
				card.universityName(),
				card.departmentName(),
				card.admissionTypeName(),
				card.band().name(),
				card.candidateKind().name(),
				roundGradeGap(card.gradeGap()),
				new Evidence(
						card.evidence().quota(),
						card.evidence().competitionRate(),
						card.evidence().newOrMergedDepartment(),
						card.evidence().basisYears()
				),
				toExplanation(explained.reason())
		);
	}

	private static WarningItem toWarning(RecommendationWarning warning, int cardCount) {
		return new WarningItem(
				warning.code().name(),
				warning.rank(),
				TemplateReportGenerator.warningText(warning.code(), cardCount)
		);
	}

	private static ReportBody toReport(ConsultingReport report) {
		return new ReportBody(
				toExplanation(report.greetingSummary()),
				toExplanation(report.overallAssessment()),
				toExplanation(report.applicationStrategy()),
				toExplanation(report.closing())
		);
	}

	private static Explanation toExplanation(ReportSection section) {
		return new Explanation(section.text(), section.generationMethod());
	}

	/** 소수 둘째 자리로 HALF_UP 반올림한다. null(UNRATED)은 그대로 null이다. */
	private static BigDecimal roundGradeGap(Double gradeGap) {
		if (gradeGap == null) {
			return null;
		}
		return BigDecimal.valueOf(gradeGap).setScale(GRADE_GAP_SCALE, RoundingMode.HALF_UP);
	}

}
