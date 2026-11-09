package com.fivegrade.backend.recommendation.probability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 표준정규분포 누적분포 Φ의 직접 구현이 기준 값과 일치하는지 검증한다.
 *
 * 기준 값은 SciPy의 norm.cdf로 계산했다.
 * 급수 구간과 연분수 구간의 경계(|z| = 4) 양쪽과 먼 꼬리를 모두 포함한다.
 */
class NormalDistributionTest {

	private static final double DELTA = 1e-10;

	@Test
	void matchesReferenceValuesInsideSeriesRange() {
		assertEquals(0.5, NormalDistribution.cdf(0), DELTA);
		assertEquals(0.841344746069, NormalDistribution.cdf(1), DELTA);
		assertEquals(0.158655253931, NormalDistribution.cdf(-1), DELTA);
		assertEquals(0.975002104852, NormalDistribution.cdf(1.96), DELTA);
		assertEquals(0.006209665326, NormalDistribution.cdf(-2.5), DELTA);
		assertEquals(0.999951903656, NormalDistribution.cdf(3.9), DELTA);
	}

	@Test
	void matchesReferenceValuesAcrossAndBeyondSeriesBoundary() {
		assertEquals(0.999979342493, NormalDistribution.cdf(4.1), DELTA);
		assertEquals(0.000000286652, NormalDistribution.cdf(-5), DELTA);
		assertEquals(0.999999999013, NormalDistribution.cdf(6), DELTA);
		assertTrue(NormalDistribution.cdf(-8) < 1e-14);
	}

	@Test
	void isSymmetricAroundZero() {
		for (double z : new double[] {0.3, 1.7, 3.99, 4.01, 7.5}) {
			assertEquals(1.0, NormalDistribution.cdf(z) + NormalDistribution.cdf(-z), 1e-12);
		}
	}

	@Test
	void returnsNaNForNaN() {
		assertTrue(Double.isNaN(NormalDistribution.cdf(Double.NaN)));
	}
}
