package com.fivegrade.backend.holistic.scoring;

/**
 * 지표 신호(count, quality)를 지표 수준(1~5)으로 바꾸는 설정. 검증 전인 가설 값을 한곳에 모아 둔다.
 *
 * 수준 = countWeight × (신호 수 단계) + qualityWeight × (1 + 4 × quality). 두 값 모두 1~5 범위다.
 * 신호 수 단계는 countCuts 로 정한다. 예를 들어 {1, 2, 4, 7} 이면
 * 0개=1, 1개=2, 2~3개=3, 4~6개=4, 7개 이상=5 다.
 * 규칙이 있는 지표인데 신호가 하나도 없으면 "근거 없음"으로 보고 1을 준다.
 *
 * 실제 생기부 표본으로 분포를 확인하기 전에는 이 값을 근거로 한 결론을 내지 않는다.
 *
 * @param countCuts     신호 수 단계의 경계(오름차순, 길이 4). 경계 이상이면 한 단계 올라간다.
 * @param countWeight   신호 수 단계의 반영 비율
 * @param qualityWeight 품질의 반영 비율. countWeight 와 합하여 1이다.
 */
public record IndicatorLevelParameters(int[] countCuts, double countWeight, double qualityWeight) {

	public static final IndicatorLevelParameters DEFAULT =
			new IndicatorLevelParameters(new int[] {1, 2, 4, 7}, 0.6, 0.4);

	public IndicatorLevelParameters {
		if (countCuts == null || countCuts.length != 4) {
			throw new IllegalArgumentException("countCuts must have 4 items");
		}
		for (int i = 1; i < countCuts.length; i++) {
			if (countCuts[i] <= countCuts[i - 1]) {
				throw new IllegalArgumentException("countCuts must be increasing");
			}
		}
		if (countWeight < 0 || qualityWeight < 0 || Math.abs(countWeight + qualityWeight - 1.0) > 1e-9) {
			throw new IllegalArgumentException("weights must be non-negative and sum to 1");
		}
		countCuts = countCuts.clone();
	}

	/** 신호 수를 1~5 단계로 바꾼다. */
	public int countLevel(int count) {
		int level = 1;
		for (int cut : countCuts) {
			if (count >= cut) {
				level++;
			}
		}
		return level;
	}

	/** 신호 수와 품질(0~1)로 지표 수준(1~5)을 만든다. */
	public double level(int count, double quality) {
		double q = Math.min(1.0, Math.max(0.0, quality));
		double level = countWeight * countLevel(count) + qualityWeight * (1 + 4 * q);
		return Math.min(5.0, Math.max(1.0, level));
	}

	/** 신호가 하나도 없는 지표의 수준 */
	public double noEvidenceLevel() {
		return 1.0;
	}

	@Override
	public int[] countCuts() {
		return countCuts.clone();
	}
}
