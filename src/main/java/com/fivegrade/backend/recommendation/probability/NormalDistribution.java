package com.fivegrade.backend.recommendation.probability;

/**
 * 표준정규분포의 누적분포함수 Φ.
 *
 * 외부 수학 라이브러리 없이 쓰기 위해 직접 구현했다.
 * |z| ≤ 4 는 오차함수의 테일러 급수, 그보다 먼 꼬리는 연분수로 계산하며
 * 절대 오차는 1e-12 안팎이다.
 */
public final class NormalDistribution {

	private static final double SQRT_TWO = Math.sqrt(2.0);
	private static final double TWO_OVER_SQRT_PI = 2.0 / Math.sqrt(Math.PI);
	private static final double INV_SQRT_TWO_PI = 1.0 / Math.sqrt(2.0 * Math.PI);
	private static final double SERIES_LIMIT = 4.0;

	private NormalDistribution() {
	}

	/** P(Z ≤ z). */
	public static double cdf(double z) {
		if (Double.isNaN(z)) {
			return Double.NaN;
		}
		double tail = upperTail(Math.abs(z));
		return z >= 0 ? 1.0 - tail : tail;
	}

	/** P(Z > a), a ≥ 0. */
	private static double upperTail(double a) {
		if (a <= SERIES_LIMIT) {
			return 0.5 * (1.0 - erf(a / SQRT_TWO));
		}
		return INV_SQRT_TWO_PI * Math.exp(-0.5 * a * a) / millsDenominator(a);
	}

	private static double erf(double x) {
		double term = x;
		double sum = x;
		for (int n = 1; n < 200; n++) {
			term *= -x * x / n;
			double contribution = term / (2 * n + 1);
			sum += contribution;
			if (Math.abs(contribution) < 1e-17) {
				break;
			}
		}
		return TWO_OVER_SQRT_PI * sum;
	}

	/** a + 1/(a + 2/(a + 3/(a + ...))) 를 뒤에서부터 계산한다. */
	private static double millsDenominator(double a) {
		double value = a;
		for (int k = 80; k >= 1; k--) {
			value = a + k / value;
		}
		return value;
	}
}
