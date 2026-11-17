package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.report.generation.ConsultingReport;
import com.fivegrade.backend.report.generation.ReportSection;
import com.fivegrade.backend.report.generation.TemplateReportGenerator;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import com.fivegrade.backend.simulation.dto.SimulationReportResult.ExplainedCard;
import com.fivegrade.backend.simulation.dto.SimulationResponse;
import com.fivegrade.backend.simulation.dto.SimulationResponse.DocumentFitItem;
import com.fivegrade.backend.simulation.dto.SimulationResponse.Evidence;
import com.fivegrade.backend.simulation.dto.SimulationResponse.Explanation;
import com.fivegrade.backend.simulation.dto.SimulationResponse.HolisticResult;
import com.fivegrade.backend.simulation.dto.SimulationResponse.RecommendationItem;
import com.fivegrade.backend.simulation.dto.SimulationResponse.ReportBody;
import com.fivegrade.backend.simulation.dto.SimulationResponse.SubjectResult;
import com.fivegrade.backend.simulation.dto.SimulationResponse.WarningItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 내부 결과(SimulationReportResult)를 공개 응답으로 바꾼다.
 *
 * 값을 새로 계산하지 않고 모양만 바꾼다. 유일한 가공은 gradeGap의 표시용 반올림이다.
 * 추천 서비스의 gradeGap은 반올림 전 값이므로 여기서 소수 둘째 자리로 한 번만 반올림한다.
 * 반올림한 값으로 구간을 다시 판단하거나 gradeGap을 다시 계산하지 않는다.
 *
 * 안내(warnings)는 기획 확정 방식에 맞춰 공개 응답에서만 다음과 같이 정리한다(#74).
 * - 선호 전형이 아닌 전형으로 넓힌 안내(OTHER_TRACK_USED)는 내려주지 않는다.
 * - 유사 학과·자율전공으로 넓힌 안내는 카드마다 붙이지 않고, 발생한 코드별로 결과 전체 안내(rank null) 한 번만 내려준다.
 * 내부 추천 결과와 카드의 candidateKind 는 그대로 둔다.
 */
public final class SimulationResponseMapper {

	private static final int GRADE_GAP_SCALE = 2;

	private static final String STATUS_APPLIED = "APPLIED";
	private static final String STATUS_SKIPPED = "SKIPPED";

	/** 서류 보정으로 옮겨 갈 수 있는 구간 */
	private static final Set<AdmissionBand> ADJUSTABLE_BANDS =
			EnumSet.of(AdmissionBand.SAFE, AdmissionBand.MATCH, AdmissionBand.REACH);

	static final String SAVE_SAVED = "SAVED";
	static final String SAVE_FAILED = "FAILED";
	static final String SAVE_NOT_SAVED = "NOT_SAVED";

	/** 카드별로 붙어 있어도 결과 전체 안내 한 번으로 모아 내려주는 범위 확장 안내. 응답 순서도 이 순서를 따른다. */
	private static final List<WarningCode> RANGE_EXPANSION_CODES =
			List.of(WarningCode.SIMILAR_MAJOR_USED, WarningCode.UNDECLARED_USED);

	private SimulationResponseMapper() {
	}

	/** 저장 결과가 없는 변환. 저장 대상이 아닌 것(NOT_SAVED)으로 내려간다. */
	public static SimulationResponse toResponse(SimulationReportResult result) {
		return toResponse(result, null);
	}

	/** @param saveOutcome 자동 저장 결과. null 이면 저장 대상이 아닌 것(NOT_SAVED)으로 본다. */
	public static SimulationResponse toResponse(SimulationReportResult result, SimulationReportSaver.SaveOutcome saveOutcome) {
		Objects.requireNonNull(result);
		int cardCount = result.recommendations().size();

		List<SubjectResult> subjects = result.subjects().stream()
				.map(subject -> new SubjectResult(subject.subjectRef(), subject.subjectCode(), subject.grade()))
				.toList();
		List<RecommendationItem> recommendations = result.recommendations().stream()
				.map(SimulationResponseMapper::toItem)
				.toList();
		List<WarningItem> warnings = toWarnings(result.warnings(), cardCount);

		return new SimulationResponse(
				result.displayAverage(),
				subjects,
				recommendations,
				warnings,
				toReport(result.report()),
				saveStatusOf(saveOutcome),
				saveOutcome != null && isSaved(saveOutcome) ? saveOutcome.reportId() : null,
				toHolistic(result.holistic())
		);
	}

	private static String saveStatusOf(SimulationReportSaver.SaveOutcome outcome) {
		if (outcome == null) {
			return SAVE_NOT_SAVED;
		}
		return switch (outcome.status()) {
			case SAVED, ALREADY_SAVED -> SAVE_SAVED;
			case FAILED -> SAVE_FAILED;
			case SKIPPED_NO_CARDS -> SAVE_NOT_SAVED;
		};
	}

	private static boolean isSaved(SimulationReportSaver.SaveOutcome outcome) {
		return SAVE_SAVED.equals(saveStatusOf(outcome));
	}

	private static HolisticResult toHolistic(HolisticEvaluation holistic) {
		if (holistic == null) {
			return HolisticResult.notProvided();
		}
		return switch (holistic) {
			case HolisticEvaluation.Applied applied -> {
				Map<String, BigDecimal> elementLevels = new TreeMap<>();
				applied.elementLevels().forEach((element, level) -> elementLevels.put(element, round(level)));
				yield new HolisticResult(
						STATUS_APPLIED, null, applied.rulesVersion(), elementLevels, round(applied.overallLevel()));
			}
			case HolisticEvaluation.Skipped skipped -> skipped.reason() == HolisticEvaluation.SkipReason.NOT_PROVIDED
					? HolisticResult.notProvided()
					: new HolisticResult(STATUS_SKIPPED, skipped.reason().name(), null, null, null);
		};
	}

	/**
	 * 서류 보정을 적용한 카드는 category 를 성적만으로 본 구간(baseBand)으로, overallCategory 를 보정 후 구간으로 내린다.
	 * 보정하지 않은 카드는 category 가 곧 구간이고 overallCategory 는 null 이다.
	 */
	private static RecommendationItem toItem(ExplainedCard explained) {
		RecommendedCard card = explained.card();
		boolean adjusted = card.baseBand() != null;
		String category = (adjusted ? card.baseBand() : card.band()).name();
		String overallCategory = adjusted && ADJUSTABLE_BANDS.contains(card.band()) ? card.band().name() : null;
		DocumentFitItem fit = card.documentFit() == null ? null : new DocumentFitItem(
				card.documentFit().level().name(), card.documentFit().basis());
		return new RecommendationItem(
				card.rank(),
				card.universityName(),
				card.departmentName(),
				card.admissionTypeName(),
				category,
				card.candidateKind().name(),
				roundGradeGap(card.gradeGap()),
				new Evidence(
						card.evidence().quota(),
						card.evidence().competitionRate(),
						card.evidence().newOrMergedDepartment(),
						card.evidence().basisYears()
				),
				toExplanation(explained.reason()),
				overallCategory,
				fit
		);
	}

	/**
	 * 결과 전체 안내, 범위 확장 안내(결과 단위 1회), 나머지 카드별 안내 순서로 내려준다.
	 * 선호 전형 안내는 제외하고, 같은 코드의 결과 전체 안내가 이미 있으면 다시 만들지 않는다.
	 */
	private static List<WarningItem> toWarnings(List<RecommendationWarning> source, int cardCount) {
		List<WarningItem> warnings = new ArrayList<>();
		for (RecommendationWarning warning : source) {
			if (warning.rank() == null) {
				warnings.add(toWarning(warning.code(), null, cardCount));
			}
		}
		for (WarningCode code : RANGE_EXPANSION_CODES) {
			boolean alreadyIncluded = warnings.stream().anyMatch(item -> item.code().equals(code.name()));
			boolean usedByCard = source.stream().anyMatch(warning -> warning.code() == code);
			if (usedByCard && !alreadyIncluded) {
				warnings.add(toWarning(code, null, cardCount));
			}
		}
		for (RecommendationWarning warning : source) {
			if (warning.rank() != null && isCardLevelWarningShown(warning.code())) {
				warnings.add(toWarning(warning.code(), warning.rank(), cardCount));
			}
		}
		return List.copyOf(warnings);
	}

	private static boolean isCardLevelWarningShown(WarningCode code) {
		return code != WarningCode.OTHER_TRACK_USED && !RANGE_EXPANSION_CODES.contains(code);
	}

	private static WarningItem toWarning(WarningCode code, Integer rank, int cardCount) {
		return new WarningItem(code.name(), rank, TemplateReportGenerator.warningText(code, cardCount));
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

	private static BigDecimal round(double value) {
		return BigDecimal.valueOf(value).setScale(GRADE_GAP_SCALE, RoundingMode.HALF_UP);
	}

	/** 소수 둘째 자리로 HALF_UP 반올림한다. null(UNRATED)은 그대로 null이다. */
	private static BigDecimal roundGradeGap(Double gradeGap) {
		if (gradeGap == null) {
			return null;
		}
		return BigDecimal.valueOf(gradeGap).setScale(GRADE_GAP_SCALE, RoundingMode.HALF_UP);
	}

}
