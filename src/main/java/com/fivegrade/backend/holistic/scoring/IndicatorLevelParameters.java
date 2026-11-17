package com.fivegrade.backend.holistic.scoring;

/**
 * 지표 신호(count, quality)를 지표 수준(1~5)으로 바꾸는 설정. 검증 전인 가설 값을 한곳에 모아 둔다.
 *
 * 수준 = rateWeight × (신호 밀도 단계) + qualityWeight × (1 + 4 × quality). 두 값 모두 1~5 범위다.
 * 신호 밀도는 읽은 서술 1000자당 신호 수다. 서술이 길수록 신호가 많이 잡히는 것이 당연하므로
 * 개수가 아니라 밀도로 단계를 정한다. 예를 들어 rateCuts 가 {0.15, 0.4, 0.9, 1.8} 이면
 * 0.15 미만=1, 0.15~0.4 미만=2, 0.4~0.9 미만=3, 0.9~1.8 미만=4, 1.8 이상=5 다.
 *
 * 경계값은 가상 생기부 50건으로 단계가 순서대로 벌어지는지만 확인한 가설이다.
 * 실제 생기부 표본으로 분포를 확인하기 전에는 이 값을 근거로 한 결론을 내지 않는다.
 *
 * @param rateCuts      신호 밀도(1000자당) 단계의 경계(오름차순, 길이 4). 경계 이상이면 한 단계 올라간다.
 * @param rateWeight    신호 밀도 단계의 반영 비율
 * @param qualityWeight 품질의 반영 비율. rateWeight 와 합하여 1이다.
 */
public record IndicatorLevelParameters(double[] rateCuts, double rateWeight, double qualityWeight) {

	public static final IndicatorLevelParameters DEFAULT =
			new IndicatorLevelParameters(new double[] {0.15, 0.4, 0.9, 1.8}, 0.6, 0.4);

	public IndicatorLevelParameters {
		if (rateCuts == null || rateCuts.length != 4) {
			throw new IllegalArgumentException("rateCuts must have 4 items");
		}
		if (rateCuts[0] <= 0) {
			throw new IllegalArgumentException("rateCuts must be positive");
		}
		for (int i = 1; i < rateCuts.length; i++) {
			if (rateCuts[i] <= rateCuts[i - 1]) {
				throw new IllegalArgumentException("rateCuts must be increasing");
			}
		}
		if (rateWeight < 0 || qualityWeight < 0 || Math.abs(rateWeight + qualityWeight - 1.0) > 1e-9) {
			throw new IllegalArgumentException("weights must be non-negative and sum to 1");
		}
		rateCuts = rateCuts.clone();
	}

	/** 신호 수를 서술 1000자당 밀도로 바꿔 1~5 단계로 만든다. 글자 수가 1보다 작으면 1자로 본다. */
	public int rateLevel(int count, long narrativeChars) {
		double rate = count * 1000.0 / Math.max(1L, narrativeChars);
		int level = 1;
		for (double cut : rateCuts) {
			if (rate >= cut) {
				level++;
			}
		}
		return level;
	}

	/** 신호 수·서술 글자 수·품질(0~1)로 지표 수준(1~5)을 만든다. */
	public double level(int count, long narrativeChars, double quality) {
		double q = Math.min(1.0, Math.max(0.0, quality));
		double level = rateWeight * rateLevel(count, narrativeChars) + qualityWeight * (1 + 4 * q);
		return Math.min(5.0, Math.max(1.0, level));
	}

	@Override
	public double[] rateCuts() {
		return rateCuts.clone();
	}
}
