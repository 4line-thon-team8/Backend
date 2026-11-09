package com.fivegrade.backend.recommendation.scale;

/**
 * 9등급제 성적과 5등급제 성적을 누적 비율로 서로 옮긴다.
 *
 * 2028 대입 5등급제의 실제 분포 데이터가 없으므로, "현행 9등급에서의 상대 위치가 같다"고 가정한 근사 참고값이다.
 * 방법은 다음과 같다.
 * 1. 각 등급 구간의 누적 비율 중앙값을 그 등급의 대표 위치로 둔다. (9등급 1등급 2.0%, 5등급 1등급 5.0% 등)
 * 2. 대표 위치 사이의 정수 아닌 등급은 선형으로 보간한다.
 * 3. 가장 좋은 등급과 가장 나쁜 등급의 대표 위치 바깥은 끝값으로 고정한다.
 *    그래서 9등급 1.0~약 1.54는 모두 5등급 1.0, 약 8.46~9.0은 모두 5.0이 된다.
 *
 * 입결(9등급)을 5등급 참고값으로 바꿀 때는 {@link #toFiveGrade(double)},
 * 사용자의 5등급 평균을 9등급 기준 모델에 넣을 때는 {@link #toNineGradeEquivalent(double)}를 쓴다.
 * 두 변환은 9등급 약 1.55~8.45 구간에서 서로 역함수다.
 *
 * Spring과 DB에 의존하지 않는 순수 Java 계산기다.
 */
public final class GradeScaleConverter {

	/** 9등급 내신 등급별 비율(%): 4, 7, 12, 17, 20, 17, 12, 7, 4 의 누적. */
	private static final double[] NINE_CUMULATIVE = {4, 11, 23, 40, 60, 77, 89, 96, 100};

	/** 5등급제 확정안의 등급별 비율(%) 10, 24, 32, 24, 10 의 누적. */
	private static final double[] FIVE_CUMULATIVE = {10, 34, 66, 90, 100};

	private static final double[] NINE_CENTERS = centers(NINE_CUMULATIVE);
	private static final double[] FIVE_CENTERS = centers(FIVE_CUMULATIVE);

	/** 9등급 성적(1.0~9.0)을 5등급 참고값(1.0~5.0)으로 바꾼다. 범위 밖이면 예외를 던진다. */
	public double toFiveGrade(double nineGrade) {
		requireInRange(nineGrade, NINE_CENTERS.length);
		double percentile = interpolate(nineGrade, NINE_CENTERS);
		return inverse(percentile, FIVE_CENTERS);
	}

	/** 5등급 성적(1.0~5.0)을 같은 상대 위치의 9등급 값(1.0~9.0)으로 바꾼다. 범위 밖이면 예외를 던진다. */
	public double toNineGradeEquivalent(double fiveGrade) {
		requireInRange(fiveGrade, FIVE_CENTERS.length);
		double percentile = interpolate(fiveGrade, FIVE_CENTERS);
		return inverse(percentile, NINE_CENTERS);
	}

	private static double[] centers(double[] cumulative) {
		double[] centers = new double[cumulative.length];
		double previous = 0;
		for (int i = 0; i < cumulative.length; i++) {
			centers[i] = (previous + cumulative[i]) / 2;
			previous = cumulative[i];
		}
		return centers;
	}

	/** 등급(1부터)을 대표 위치 사이에서 선형 보간해 누적 비율로 바꾼다. */
	private static double interpolate(double grade, double[] centers) {
		int lower = (int) Math.floor(grade);
		if (lower >= centers.length) {
			return centers[centers.length - 1];
		}
		double fraction = grade - lower;
		return centers[lower - 1] + fraction * (centers[lower] - centers[lower - 1]);
	}

	/** 누적 비율을 대표 위치 사이에서 선형 보간해 등급으로 바꾼다. 양끝 바깥은 끝 등급으로 고정한다. */
	private static double inverse(double percentile, double[] centers) {
		if (percentile <= centers[0]) {
			return 1.0;
		}
		int last = centers.length - 1;
		if (percentile >= centers[last]) {
			return centers.length;
		}
		int i = 0;
		while (centers[i + 1] < percentile) {
			i++;
		}
		return (i + 1) + (percentile - centers[i]) / (centers[i + 1] - centers[i]);
	}

	private static void requireInRange(double grade, int maxGrade) {
		if (Double.isNaN(grade) || grade < 1.0 || grade > maxGrade) {
			throw new IllegalArgumentException("grade is out of range");
		}
	}
}
