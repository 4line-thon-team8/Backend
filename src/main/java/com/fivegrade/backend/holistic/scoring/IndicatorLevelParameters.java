package com.fivegrade.backend.holistic.scoring;

import java.util.HashMap;
import java.util.Map;

/**
 * 지표 신호(count, quality)를 지표 수준(1~5)으로 바꾸는 설정. 검증 전인 가설 값을 한곳에 모아 둔다.
 *
 * 수준 = rateWeight × (신호 밀도 단계) + qualityWeight × (1 + 4 × 정규화한 품질). 두 값 모두 1~5 범위다.
 * 신호 밀도는 읽은 서술 1000자당 신호 수다. 서술이 길수록 신호가 많이 잡히는 것이 당연하므로
 * 개수가 아니라 밀도로 단계를 정한다. 경계가 {a, b, c, d} 이면
 * a 미만=1, a~b 미만=2, b~c 미만=3, c~d 미만=4, d 이상=5 다.
 *
 * <h3>지표별 경계</h3>
 * 지표마다 신호가 잡히는 정도가 크게 다르다. 실제 생기부 21건에서 1000자당 밀도의 중앙값이 A3(탐구력)은 4.6,
 * C2는 0.8 이다. 모든 지표에 같은 경계를 쓰면 A3 는 거의 모든 학생이 5단계, C2 는 거의 모든 학생이 낮은 단계가 되어
 * 학생끼리 구분이 되지 않는다. 그래서 지표별로 실제 문서 분포의 10·30·70·90 백분위를 경계로 쓴다.
 * 이는 서류 수준 평가 안내서의 기준(5=상위 10%, 4=10~30%, 3=30~70%, 2=하위 10~30%, 1=하위 10%)과 같은 비율이다.
 * 지표별 경계가 없는 지표(규칙이 없는 지표 등)는 rateCuts(공통 경계)를 쓴다.
 *
 * <h3>품질 정규화</h3>
 * 품질은 앱이 150자 덩어리마다 재서 평균한 값(문단 길이와 무관)이다. 실제 21건에서 p10=0.10, p90=0.32 였다.
 * qualityFloor 이하를 1점, qualityCeil 이상을 5점으로 늘려 쓴다. 정규화 없이 1 + 4 × 품질을 쓰면 실제 문서가
 * 모두 1.4~2.3 점대에 몰려 품질이 구분 기준이 되지 못한다.
 *
 * 경계값은 실제 생기부 21건에 맞춘 값이다. 사람이 매긴 서류 수준으로 검증하기 전까지는 그 점을 감안한다.
 *
 * @param rateCuts       공통 신호 밀도(1000자당) 경계(오름차순, 길이 4). 지표별 경계가 없을 때 쓴다.
 * @param indicatorCuts  지표 ID → 그 지표의 신호 밀도 경계(오름차순, 길이 4)
 * @param rateWeight     신호 밀도 단계의 반영 비율
 * @param qualityWeight  품질의 반영 비율. rateWeight 와 합하여 1이다.
 * @param qualityFloor   품질을 1점으로 보는 값(이하)
 * @param qualityCeil    품질을 5점으로 보는 값(이상). qualityFloor 보다 크다.
 */
public record IndicatorLevelParameters(
		double[] rateCuts, Map<String, double[]> indicatorCuts, double rateWeight, double qualityWeight,
		double qualityFloor, double qualityCeil) {

	public static final IndicatorLevelParameters DEFAULT = new IndicatorLevelParameters(
			new double[] {0.7, 1.2, 1.7, 2.5},
			Map.of(
				"A2", new double[] {1.29, 1.80, 2.62, 2.94},
				"A3", new double[] {3.89, 4.25, 5.31, 6.64},
				"B3", new double[] {0.58, 1.09, 2.20, 2.75},
				"C1", new double[] {1.15, 1.54, 2.18, 3.03},
				"C2", new double[] {0.28, 0.64, 1.03, 1.34},
				"C3", new double[] {0.77, 1.26, 1.90, 2.23},
				"C4", new double[] {0.38, 0.53, 1.01, 1.69},
				"D1", new double[] {0.52, 0.71, 1.21, 1.58},
				"D3", new double[] {1.16, 1.80, 2.43, 2.71},
				"D4", new double[] {0.30, 0.87, 1.46, 2.06}),
			0.6, 0.4, 0.10, 0.32);

	/** 지표별 경계와 품질 정규화 없이 공통 경계만 쓰는 설정(품질은 0~1 을 그대로 1~5 로 편다). */
	public IndicatorLevelParameters(double[] rateCuts, double rateWeight, double qualityWeight) {
		this(rateCuts, Map.of(), rateWeight, qualityWeight, 0.0, 1.0);
	}

	public IndicatorLevelParameters {
		checkCuts(rateCuts, "rateCuts");
		if (indicatorCuts == null) {
			throw new IllegalArgumentException("indicatorCuts must not be null");
		}
		Map<String, double[]> copy = new HashMap<>();
		indicatorCuts.forEach((id, cuts) -> {
			checkCuts(cuts, "indicatorCuts[" + id + "]");
			copy.put(id, cuts.clone());
		});
		if (rateWeight < 0 || qualityWeight < 0 || Math.abs(rateWeight + qualityWeight - 1.0) > 1e-9) {
			throw new IllegalArgumentException("weights must be non-negative and sum to 1");
		}
		if (qualityFloor < 0 || qualityCeil > 1 || qualityFloor >= qualityCeil) {
			throw new IllegalArgumentException("quality range must satisfy 0 <= floor < ceil <= 1");
		}
		rateCuts = rateCuts.clone();
		indicatorCuts = Map.copyOf(copy);
	}

	private static void checkCuts(double[] cuts, String name) {
		if (cuts == null || cuts.length != 4) {
			throw new IllegalArgumentException(name + " must have 4 items");
		}
		if (cuts[0] <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		for (int i = 1; i < cuts.length; i++) {
			if (cuts[i] <= cuts[i - 1]) {
				throw new IllegalArgumentException(name + " must be increasing");
			}
		}
	}

	/** 공통 경계로 신호 수를 서술 1000자당 밀도로 바꿔 1~5 단계로 만든다. 글자 수가 1보다 작으면 1자로 본다. */
	public int rateLevel(int count, long narrativeChars) {
		return stage(rateCuts, count, narrativeChars);
	}

	/** 그 지표의 경계(없으면 공통 경계)로 1~5 단계를 만든다. */
	public int rateLevel(String indicatorId, int count, long narrativeChars) {
		return stage(indicatorCuts.getOrDefault(indicatorId, rateCuts), count, narrativeChars);
	}

	private static int stage(double[] cuts, int count, long narrativeChars) {
		double rate = count * 1000.0 / Math.max(1L, narrativeChars);
		int level = 1;
		for (double cut : cuts) {
			if (rate >= cut) {
				level++;
			}
		}
		return level;
	}

	/** 신호 수·서술 글자 수·품질(0~1)로 지표 수준(1~5)을 만든다(공통 경계). */
	public double level(int count, long narrativeChars, double quality) {
		return combine(rateLevel(count, narrativeChars), quality);
	}

	/** 지표별 경계로 지표 수준(1~5)을 만든다. */
	public double level(String indicatorId, int count, long narrativeChars, double quality) {
		return combine(rateLevel(indicatorId, count, narrativeChars), quality);
	}

	private double combine(int rateStage, double quality) {
		double q = Math.min(1.0, Math.max(0.0, (quality - qualityFloor) / (qualityCeil - qualityFloor)));
		double level = rateWeight * rateStage + qualityWeight * (1 + 4 * q);
		return Math.min(5.0, Math.max(1.0, level));
	}

	@Override
	public double[] rateCuts() {
		return rateCuts.clone();
	}

	@Override
	public Map<String, double[]> indicatorCuts() {
		Map<String, double[]> copy = new HashMap<>();
		indicatorCuts.forEach((id, cuts) -> copy.put(id, cuts.clone()));
		return copy;
	}
}
