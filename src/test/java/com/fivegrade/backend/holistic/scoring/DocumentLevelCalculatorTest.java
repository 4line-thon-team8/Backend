package com.fivegrade.backend.holistic.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.AxisWeights;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.ElementLevels;
import org.junit.jupiter.api.Test;

class DocumentLevelCalculatorTest {

	private static final double DELTA = 1e-9;

	private final DocumentLevelCalculator calculator = new DocumentLevelCalculator();

	@Test
	void weightsAreApplied() {
		ElementLevels levels = new ElementLevels(4.0, 2.0, 3.0);
		AxisWeights weights = new AxisWeights(50.0, 30.0, 20.0);

		assertEquals(0.5 * 4 + 0.3 * 2 + 0.2 * 3, calculator.compute(levels, weights).orElseThrow(), DELTA);
	}

	@Test
	void unknownWeightsFallBackToEqual() {
		ElementLevels levels = new ElementLevels(4.0, 2.0, 3.0);

		assertEquals(3.0, calculator.compute(levels, AxisWeights.equal()).orElseThrow(), DELTA);
	}

	@Test
	void insufficientAxisIsExcludedAndWeightsRenormalized() {
		ElementLevels levels = new ElementLevels(4.0, null, 2.0);
		AxisWeights weights = new AxisWeights(60.0, 30.0, 10.0);

		assertEquals((0.6 * 4 + 0.1 * 2) / 0.7, calculator.compute(levels, weights).orElseThrow(), DELTA);
	}

	@Test
	void fewerThanTwoUsableAxesGivesNoLevel() {
		ElementLevels levels = new ElementLevels(4.0, null, null);

		assertTrue(calculator.compute(levels, AxisWeights.equal()).isEmpty());
	}

	@Test
	void zeroWeightsOnUsableAxesFallBackToEqual() {
		ElementLevels levels = new ElementLevels(4.0, 2.0, null);
		AxisWeights weights = new AxisWeights(0.0, 0.0, 100.0);

		assertEquals(3.0, calculator.compute(levels, weights).orElseThrow(), DELTA);
	}

}
