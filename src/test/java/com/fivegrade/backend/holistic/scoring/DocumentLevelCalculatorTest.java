package com.fivegrade.backend.holistic.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.AxisWeights;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.ElementLevels;
import org.junit.jupiter.api.Test;

class DocumentLevelCalculatorTest {

	private static final double DELTA = 1e-9;

	/** 가중 평균 계산만 확인하는 테스트는 분포 보정 없이(항등) 돌린다. */
	private final DocumentLevelCalculator calculator = new DocumentLevelCalculator(DocumentLevelSpread.IDENTITY);

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
	void noUsableAxisGivesNoLevel() {
		ElementLevels levels = new ElementLevels(null, null, null);

		assertTrue(calculator.compute(levels, AxisWeights.equal()).isEmpty());
	}

	@Test
	void singleUsableAxisGivesThatAxisLevel() {
		ElementLevels levels = new ElementLevels(null, 2.5, null);
		AxisWeights weights = new AxisWeights(50.0, 30.0, 20.0);

		assertEquals(2.5, calculator.compute(levels, weights).orElseThrow(), DELTA);
		assertEquals(2.5, calculator.compute(levels, AxisWeights.equal()).orElseThrow(), DELTA);
	}

	@Test
	void zeroWeightsOnUsableAxesFallBackToEqual() {
		ElementLevels levels = new ElementLevels(4.0, 2.0, null);
		AxisWeights weights = new AxisWeights(0.0, 0.0, 100.0);

		assertEquals(3.0, calculator.compute(levels, weights).orElseThrow(), DELTA);
	}

	@Test
	void 기본_계산기는_가중_평균에_분포_보정을_적용한다() {
		DocumentLevelCalculator production = new DocumentLevelCalculator();
		ElementLevels levels = new ElementLevels(4.0, 2.0, 3.0);
		AxisWeights weights = new AxisWeights(50.0, 30.0, 20.0);
		double mean = calculator.compute(levels, weights).orElseThrow();

		assertEquals(DocumentLevelSpread.DEFAULT.apply(mean), production.compute(levels, weights).orElseThrow(), DELTA);
		// 보정은 순서를 바꾸지 않는다.
		double lower = production.compute(new ElementLevels(3.0, 2.0, 3.0), weights).orElseThrow();
		double higher = production.compute(new ElementLevels(4.0, 2.0, 3.0), weights).orElseThrow();
		assertTrue(lower < higher);
	}

}
