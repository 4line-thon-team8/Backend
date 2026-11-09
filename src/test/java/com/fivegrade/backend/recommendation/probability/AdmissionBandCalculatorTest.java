package com.fivegrade.backend.recommendation.probability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 예측 컷과 학생 등급으로 가능성 구간을 나누는 규칙을 검증한다.
 *
 * 가능성 기준 값은 SciPy의 norm.cdf로 계산했다.
 * 기본 예측: 70%컷 3.14, 폭 1.0, 정상 컷 3개 연도 → 중심 3.09, σ 0.35 + 0.15 * 1.0 = 0.50
 *
 * DB나 Spring 없이 실행한다.
 */
class AdmissionBandCalculatorTest {

	private static final double DELTA = 1e-6;

	private final AdmissionBandCalculator calculator = new AdmissionBandCalculator();
	private final CutForecast threeYears = new CutForecast(3.14, 1.0, 3);

	@Test
	void calculatesProbabilityAndMargin() {
		BandAssessment atCenter = calculator.assess(threeYears, 3.09);
		assertEquals(0.5, atCenter.probability().getAsDouble(), DELTA);
		assertEquals(0.0, atCenter.margin().getAsDouble(), DELTA);

		BandAssessment better = calculator.assess(threeYears, 2.0);
		assertEquals(0.985371, better.probability().getAsDouble(), DELTA);
		assertEquals(1.09, better.margin().getAsDouble(), DELTA);
		assertEquals(3, better.basisYears());
	}

	@Test
	void classifiesAcrossAllBands() {
		assertEquals(AdmissionBand.SAFE, calculator.assess(threeYears, 2.0).band());
		assertEquals(AdmissionBand.SAFE, calculator.assess(threeYears, 2.5).band());
		assertEquals(AdmissionBand.MATCH, calculator.assess(threeYears, 2.7).band());
		assertEquals(AdmissionBand.MATCH, calculator.assess(threeYears, 3.0).band());
		assertEquals(AdmissionBand.REACH, calculator.assess(threeYears, 3.3).band());
		assertEquals(AdmissionBand.REACH, calculator.assess(threeYears, 3.6).band());
		assertEquals(AdmissionBand.FAR, calculator.assess(threeYears, 4.0).band());
	}

	@Test
	void marksVeryHighGradesAsOver() {
		BandAssessment assessment = calculator.assess(threeYears, 1.0);

		assertEquals(AdmissionBand.OVER, assessment.band());
		assertEquals(2.09, assessment.margin().getAsDouble(), DELTA);
	}

	@Test
	void doesNotMarkOverWhenMarginIsAtMostOnePointFive() {
		// 중심 6.45, 학생 5.0 → 여유 1.45 < 1.5이므로 가능성이 0.99를 넘어도 과잉이 아니다.
		CutForecast forecast = new CutForecast(6.5, 0.5, 2);

		BandAssessment assessment = calculator.assess(forecast, 5.0);

		assertEquals(0.997565, assessment.probability().getAsDouble(), DELTA);
		assertEquals(AdmissionBand.SAFE, assessment.band());
	}

	@Test
	void appliesStricterSafeRuleToLowGrades() {
		// 중심 5.45, σ 0.35 + 0.15 * 0.5 = 0.425
		CutForecast forecast = new CutForecast(5.5, 0.5, 3);

		BandAssessment justBelowBoundary = calculator.assess(forecast, 4.99);
		BandAssessment atBoundary = calculator.assess(forecast, 5.0);

		// 5.0 미만은 기본 기준(0.85)이라 안정, 5.0부터는 0.96이 필요해 적정이 된다.
		assertEquals(0.860452, justBelowBoundary.probability().getAsDouble(), DELTA);
		assertEquals(AdmissionBand.SAFE, justBelowBoundary.band());
		assertEquals(0.855160, atBoundary.probability().getAsDouble(), DELTA);
		assertEquals(AdmissionBand.MATCH, atBoundary.band());
	}

	@Test
	void requiresTwoCleanYearsForSafeWithLowGrades() {
		// 같은 학생 5.0, 가능성은 0.96을 넘지만 정상 컷이 1개 연도뿐이면 안정으로 보지 않는다.
		CutForecast oneYear = new CutForecast(6.5, 0.5, 1);
		CutForecast twoYears = new CutForecast(6.5, 0.5, 2);

		BandAssessment withOneYear = calculator.assess(oneYear, 5.0);
		BandAssessment withTwoYears = calculator.assess(twoYears, 5.0);

		assertEquals(0.982861, withOneYear.probability().getAsDouble(), DELTA);
		assertEquals(AdmissionBand.MATCH, withOneYear.band());
		assertEquals(AdmissionBand.SAFE, withTwoYears.band());
	}

	@Test
	void usesWideSigmaForFallbackForecast() {
		// 정상 컷이 없는 경우 σ = 0.95 + 0.15 * 2.0 = 1.25, 중심 3.95
		CutForecast fallback = new CutForecast(4.0, 2.0, 0);

		BandAssessment assessment = calculator.assess(fallback, 3.95);

		assertEquals(0.5, assessment.probability().getAsDouble(), DELTA);
		assertEquals(AdmissionBand.MATCH, assessment.band());
		assertEquals(0, assessment.basisYears());
	}

	@Test
	void returnsUnratedWithoutForecast() {
		BandAssessment assessment = calculator.assess(null, 3.0);

		assertEquals(AdmissionBand.UNRATED, assessment.band());
		assertFalse(assessment.probability().isPresent());
		assertFalse(assessment.margin().isPresent());
	}

	@Test
	void defaultParametersMatchTheValidatedModel() {
		ProbabilityParameters parameters = ProbabilityParameters.DEFAULT;

		assertEquals(-0.05, parameters.driftShift(), DELTA);
		assertEquals(0.95, parameters.baseSigma(0), DELTA);
		assertEquals(0.61, parameters.baseSigma(1), DELTA);
		assertEquals(0.44, parameters.baseSigma(2), DELTA);
		assertEquals(0.35, parameters.baseSigma(3), DELTA);
		assertEquals(0.35, parameters.baseSigma(5), DELTA);
	}
}
