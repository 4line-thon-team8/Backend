package com.fivegrade.backend.recommendation.selection;

import com.fivegrade.backend.holistic.scoring.DocumentAdjuster;
import com.fivegrade.backend.holistic.scoring.DocumentAdjustmentParameters;
import com.fivegrade.backend.holistic.scoring.DocumentAdjustment;
import com.fivegrade.backend.holistic.scoring.DocumentSignal;
import com.fivegrade.backend.recommendation.candidate.CandidateUnit;
import com.fivegrade.backend.recommendation.candidate.UnitYearData;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.recommendation.probability.AdmissionBandCalculator;
import com.fivegrade.backend.recommendation.probability.BandAssessment;
import com.fivegrade.backend.recommendation.probability.CutForecast;
import com.fivegrade.backend.recommendation.probability.CutForecaster;
import com.fivegrade.backend.recommendation.probability.ProbabilityParameters;
import com.fivegrade.backend.recommendation.probability.TrackKind;
import com.fivegrade.backend.recommendation.probability.YearlyCut;
import com.fivegrade.backend.recommendation.scale.GradeScaleConverter;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 후보 모집단위의 과거 입결로 합격 가능성 구간과 gradeGap, 근거 지표를 계산한다.
 *
 * 컷 예측, 구간 판정, 등급 환산은 각 계산기에 맡기고 이 클래스는 순서만 잇는다.
 * Spring과 DB에 의존하지 않는 순수 Java 클래스다.
 */
public final class CandidateScorer {

	private final CutForecaster forecaster;
	private final AdmissionBandCalculator bandCalculator;
	private final GradeScaleConverter scaleConverter;
	private final ProbabilityParameters parameters;
	private final DocumentAdjuster documentAdjuster;

	public CandidateScorer() {
		this(ProbabilityParameters.DEFAULT);
	}

	public CandidateScorer(ProbabilityParameters parameters) {
		this.forecaster = new CutForecaster();
		this.bandCalculator = new AdmissionBandCalculator(parameters);
		this.scaleConverter = new GradeScaleConverter();
		this.parameters = parameters;
		this.documentAdjuster = new DocumentAdjuster(
				DocumentAdjustmentParameters.DEFAULT, parameters);
	}

	/**
	 * @param unit         후보 모집단위
	 * @param recentYears  전체 데이터에서 정상 컷이 있는 최근 연도(최신부터, 최대 3개)
	 * @param studentNine  컷과 같은 기준(9등급)으로 바꾼 사용자 성적
	 * @param fiveAverage  반올림 전 사용자 5등급 평균. gradeGap 계산에 쓴다.
	 */
	public ScoredCandidate score(
			CandidateUnit unit,
			List<Integer> recentYears,
			double studentNine,
			double fiveAverage
	) {
		return score(unit, recentYears, studentNine, fiveAverage, null);
	}

	/**
	 * 서류 보정을 반영한 점수 계산. 학종 서술 분석 결과가 있을 때 후보 전형별 서류 입력을 넘긴다.
	 * documentSignal 이 null 이면 서류를 반영하지 않으며 결과는 기존 계산과 같다.
	 *
	 * @param documentSignal 이 후보 전형 기준의 서류 수준·비중. 반영하지 않으면 null
	 */
	public ScoredCandidate score(
			CandidateUnit unit,
			List<Integer> recentYears,
			double studentNine,
			double fiveAverage,
			DocumentSignal documentSignal
	) {
		List<YearlyCut> cuts = unit.years().stream()
				.filter(UnitYearData::hasBothCuts)
				.map(year -> new YearlyCut(year.year(), year.cut50(), year.cut70()))
				.toList();

		CutForecast forecast = forecaster
				.forecast(cuts, recentYears, TrackKind.fromLabel(unit.trackType()))
				.orElse(null);

		BandAssessment assessment = bandCalculator.assess(forecast, studentNine);

		Double gradeGap = null;
		boolean lowConfidence = false;
		if (forecast != null) {
			gradeGap = scaleConverter.toFiveGrade(forecast.high()) - fiveAverage;
			lowConfidence = parameters.isLowConfidence(forecast);
		}

		Double probability = assessment.probability().isPresent()
				? assessment.probability().getAsDouble()
				: null;

		TrackKind trackKind = TrackKind.fromLabel(unit.trackType());
		DocumentAdjustment adjustment = null;
		AdmissionBand band = assessment.band();
		Double documentShare = documentSignal == null ? null : documentSignal.share();
		if (documentSignal != null) {
			adjustment = documentAdjuster.adjust(
					forecast, studentNine, assessment, documentSignal.level(), documentSignal.share());
			if (adjustment.applied()) {
				band = adjustment.adjustedBand();
				probability = adjustment.adjustedProbability();
			}
		}

		return new ScoredCandidate(
				unit.unitId(),
				unit.universityId(),
				unit.universityName(),
				unit.departmentName(),
				unit.trackName(),
				unit.kind(),
				band,
				probability,
				gradeGap,
				evidenceOf(unit, assessment.basisYears()),
				lowConfidence,
				trackKind,
				documentShare,
				adjustment,
				documentSignal
		);
	}

	/** 근거 지표는 값이 있는 가장 최근 연도의 것을 쓴다. 적재되지 않은 값은 null로 남긴다. */
	private CardEvidence evidenceOf(CandidateUnit unit, int basisYears) {
		return new CardEvidence(
				latest(unit, UnitYearData::quota),
				latest(unit, UnitYearData::competitionRate),
				latest(unit, UnitYearData::newOrMerged),
				basisYears
		);
	}

	private static <T> T latest(CandidateUnit unit, Function<UnitYearData, T> value) {
		return unit.years().stream()
				.filter(year -> value.apply(year) != null)
				.max(Comparator.comparingInt(UnitYearData::year))
				.map(value)
				.orElse(null);
	}

}
