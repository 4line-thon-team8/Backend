package com.fivegrade.backend.holistic.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.recommendation.probability.AdmissionBandCalculator;
import com.fivegrade.backend.recommendation.probability.BandAssessment;
import com.fivegrade.backend.recommendation.probability.CutForecast;
import org.junit.jupiter.api.Test;

/**
 * 서류 보정의 방향·크기·한계(한 구간 상한, 보정 제외 구간)를 검증한다. DB나 Spring 없이 실행한다.
 */
class DocumentAdjusterTest {

	private static final double DELTA = 1e-9;

	private final DocumentAdjustmentParameters parameters = DocumentAdjustmentParameters.DEFAULT;
	private final AdmissionBandCalculator calculator = new AdmissionBandCalculator();
	private final DocumentAdjuster adjuster = new DocumentAdjuster();

	@Test
	void lambdaIsZeroAtNeutralAndSymmetricInShapeButWeakerDownward() {
		assertEquals(0.0, parameters.lambda(3.0), DELTA);
		assertEquals(0.8, parameters.lambda(5.0), DELTA);
		assertEquals(0.4, parameters.lambda(4.0), DELTA);
		assertEquals(-0.4, parameters.lambda(1.0), DELTA);
		assertEquals(-0.2, parameters.lambda(2.0), DELTA);
	}

	@Test
	void lambdaIsClampedOutsideScale() {
		assertEquals(0.8, parameters.lambda(9.0), DELTA);
		assertEquals(-0.4, parameters.lambda(-3.0), DELTA);
	}

	@Test
	void shareFactorIsFullFromSeventyPercentAndHalfWhenUnknown() {
		assertEquals(1.0, parameters.shareFactor(1.0), DELTA);
		assertEquals(1.0, parameters.shareFactor(0.7), DELTA);
		assertEquals(0.4 / 0.7, parameters.shareFactor(0.4), DELTA);
		assertEquals(0.0, parameters.shareFactor(0.0), DELTA);
		assertEquals(0.5, parameters.shareFactor(null), DELTA);
	}

	@Test
	void noDocumentLevelMeansNoAdjustment() {
		CutForecast forecast = new CutForecast(3.0, 0.3, 3);
		BandAssessment base = calculator.assess(forecast, 3.2);

		DocumentAdjustment result = adjuster.adjust(forecast, 3.2, base, null, 1.0);

		assertFalse(result.applied());
		assertEquals(DocumentAdjustment.Reason.NO_DOCUMENT_LEVEL, result.reason());
		assertEquals(base.band(), result.adjustedBand());
		assertEquals(0.0, result.gradeShift(), DELTA);
	}

	@Test
	void neutralLevelDoesNotMoveProbability() {
		CutForecast forecast = new CutForecast(3.0, 0.3, 3);
		BandAssessment base = calculator.assess(forecast, 3.2);

		DocumentAdjustment result = adjuster.adjust(forecast, 3.2, base, 3.0, 1.0);

		assertTrue(result.applied());
		assertEquals(0.0, result.gradeShift(), DELTA);
		assertEquals(base.probability().getAsDouble(), result.adjustedProbability(), DELTA);
		assertEquals(base.band(), result.adjustedBand());
	}

	@Test
	void highDocumentLevelRaisesProbabilityByWidthScaledShift() {
		CutForecast forecast = new CutForecast(3.0, 0.3, 3);
		BandAssessment base = calculator.assess(forecast, 3.2);

		DocumentAdjustment result = adjuster.adjust(forecast, 3.2, base, 5.0, 1.0);

		double sigmaUnit = 0.3 / 0.524;
		assertEquals(0.8 * sigmaUnit, result.gradeShift(), DELTA);
		assertTrue(result.adjustedProbability() > base.probability().getAsDouble());
	}

	@Test
	void lowDocumentLevelLowersProbabilityByHalfTheUpwardStrength() {
		CutForecast forecast = new CutForecast(3.0, 0.3, 3);
		BandAssessment base = calculator.assess(forecast, 3.0);

		DocumentAdjustment up = adjuster.adjust(forecast, 3.0, base, 5.0, 1.0);
		DocumentAdjustment down = adjuster.adjust(forecast, 3.0, base, 1.0, 1.0);

		assertTrue(down.adjustedProbability() < base.probability().getAsDouble());
		assertEquals(-up.gradeShift() / 2, down.gradeShift(), DELTA);
	}

	@Test
	void narrowCutWidthUsesFloorSoShiftIsNotZero() {
		CutForecast forecast = new CutForecast(3.0, 0.0, 3);
		BandAssessment base = calculator.assess(forecast, 3.0);

		DocumentAdjustment result = adjuster.adjust(forecast, 3.0, base, 5.0, 1.0);

		assertEquals(0.8 * (0.10 / 0.524), result.gradeShift(), DELTA);
	}

	@Test
	void unknownShareHalvesTheShiftComparedWithFullShare() {
		CutForecast forecast = new CutForecast(3.0, 0.3, 3);
		BandAssessment base = calculator.assess(forecast, 3.2);

		DocumentAdjustment full = adjuster.adjust(forecast, 3.2, base, 5.0, 1.0);
		DocumentAdjustment unknown = adjuster.adjust(forecast, 3.2, base, 5.0, null);

		assertEquals(full.gradeShift() / 2, unknown.gradeShift(), DELTA);
	}

	@Test
	void bandNeverMovesMoreThanOneStep() {
		// 폭이 매우 넓은 전형에서 서류가 최고여도 소신이 안정으로 두 단계 뛰지 않는다.
		CutForecast forecast = new CutForecast(3.0, 1.0, 3);
		BandAssessment base = calculator.assess(forecast, 3.4);
		assertEquals(AdmissionBand.REACH, base.band());

		DocumentAdjustment result = adjuster.adjust(forecast, 3.4, base, 5.0, 1.0);

		// 보정 계산만으로는 안정까지 오르지만, 한 단계 상한 때문에 적정에서 멈춘다.
		assertEquals(AdmissionBand.MATCH, result.adjustedBand());
		assertTrue(result.upgraded());
	}

	@Test
	void farUnratedAndOverAreNotAdjusted() {
		CutForecast forecast = new CutForecast(3.0, 0.3, 3);

		BandAssessment far = calculator.assess(forecast, 7.0);
		assertEquals(AdmissionBand.FAR, far.band());
		assertEquals(DocumentAdjustment.Reason.BAND_NOT_ADJUSTABLE,
				adjuster.adjust(forecast, 7.0, far, 5.0, 1.0).reason());

		BandAssessment over = calculator.assess(forecast, 1.0);
		assertEquals(AdmissionBand.OVER, over.band());
		assertEquals(DocumentAdjustment.Reason.BAND_NOT_ADJUSTABLE,
				adjuster.adjust(forecast, 1.0, over, 1.0, 1.0).reason());

		BandAssessment unrated = BandAssessment.unrated();
		DocumentAdjustment result = adjuster.adjust(null, 3.0, unrated, 5.0, 1.0);
		assertFalse(result.applied());
		assertNull(result.adjustedProbability());
	}

	@Test
	void adjustedAssessmentWithZeroShiftEqualsOriginal() {
		CutForecast forecast = new CutForecast(3.0, 0.3, 3);

		BandAssessment original = calculator.assess(forecast, 3.2);
		BandAssessment zero = calculator.assess(forecast, 3.2, 0.0, 0.0);

		assertEquals(original, zero);
	}

}
