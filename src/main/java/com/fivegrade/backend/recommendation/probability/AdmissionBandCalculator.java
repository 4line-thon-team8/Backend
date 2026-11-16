package com.fivegrade.backend.recommendation.probability;

import java.util.OptionalDouble;

/**
 * 예측 컷과 학생 등급으로 합격 가능성 구간을 계산한다.
 *
 * 예측 중심 c = 예측 70%컷 + driftShift 이고,
 * 가능성 p = Φ((c - 학생 등급) / σ) 이다. σ는 정상 컷 연수가 많을수록 작고,
 * 두 컷의 폭이 클수록 크다.
 *
 * 학생 등급은 컷과 같은 등급 기준(9등급 환산, 작을수록 우수)이어야 한다.
 * 사용자가 입력한 5등급 평균을 이 기준으로 바꾸는 일은 환산 계층의 책임이다.
 *
 * Spring과 DB에 의존하지 않는 순수 Java 계산기다.
 */
public final class AdmissionBandCalculator {

	private final ProbabilityParameters parameters;

	public AdmissionBandCalculator() {
		this(ProbabilityParameters.DEFAULT);
	}

	public AdmissionBandCalculator(ProbabilityParameters parameters) {
		this.parameters = parameters;
	}

	/**
	 * @param forecast     과거 입결 예측. 사용할 컷이 없으면 null이며 UNRATED가 된다.
	 * @param studentGrade 컷과 같은 기준의 학생 등급
	 */
	public BandAssessment assess(CutForecast forecast, double studentGrade) {
		return assess(forecast, studentGrade, 0.0, 0.0);
	}

	/**
	 * 서류 보정을 반영한 평가. 학생 등급을 gradeShift만큼 우수한 쪽으로 옮기고, σ에 extraSigma를 제곱합으로 더한다.
	 * 하위 성적 판정(lowGradeStart)은 보정 전 학생 등급으로 한다. 서류가 좋아도 하위 성적의 안정 기준은 완화하지 않는다.
	 *
	 * @param gradeShift 학생 등급에서 뺄 값. 양수면 유리하다. 0이면 보정 없음
	 * @param extraSigma 추가 불확실성 σ. 0이면 더하지 않는다.
	 */
	public BandAssessment assess(CutForecast forecast, double studentGrade, double gradeShift, double extraSigma) {
		if (forecast == null) {
			return BandAssessment.unrated();
		}

		double center = forecast.high() + parameters.driftShift();
		double baseSigma = parameters.sigma(forecast);
		double sigma = extraSigma == 0.0 ? baseSigma : Math.sqrt(baseSigma * baseSigma + extraSigma * extraSigma);
		double margin = center - (studentGrade - gradeShift);
		double probability = NormalDistribution.cdf(margin / sigma);

		return new BandAssessment(
				classify(probability, margin, studentGrade, forecast.years()),
				OptionalDouble.of(probability),
				OptionalDouble.of(margin),
				forecast.years()
		);
	}

	private AdmissionBand classify(
			double probability,
			double margin,
			double studentGrade,
			int years
	) {
		if (probability >= parameters.overProbability() && margin > parameters.overGap() + ProbabilityParameters.EPSILON) {
			return AdmissionBand.OVER;
		}

		boolean lowGrade = studentGrade >= parameters.lowGradeStart() - ProbabilityParameters.EPSILON;
		double safeThreshold = lowGrade ? parameters.lowGradeSafe() : parameters.safe();
		boolean enoughYears = !lowGrade || years >= parameters.lowGradeMinYears();

		if (probability >= safeThreshold && enoughYears) {
			return AdmissionBand.SAFE;
		}
		if (probability >= parameters.match()) {
			return AdmissionBand.MATCH;
		}
		if (probability >= parameters.reach()) {
			return AdmissionBand.REACH;
		}
		return AdmissionBand.FAR;
	}
}
