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
 * 지표마다 신호가 잡히는 정도가 크게 다르다. 실제 생기부 110건(충북 일반고 사례집)에서 1000자당 밀도의 중앙값이 A3(탐구력)은 3.2,
 * C2는 0.7 이다. 모든 지표에 같은 경계를 쓰면 A3 는 거의 모든 학생이 5단계, C2 는 거의 모든 학생이 낮은 단계가 되어
 * 학생끼리 구분이 되지 않는다. 그래서 지표별로 실제 문서 분포의 10·30·70·90 백분위를 경계로 쓴다.
 * 이는 서류 수준 평가 안내서의 기준(5=상위 10%, 4=10~30%, 3=30~70%, 2=하위 10~30%, 1=하위 10%)과 같은 비율이다.
 * 경계는 앱에 포함된 구절 분류 모델·품질 모델로 위 110건을 분석한 신호의 지표별 백분위다.
 * 지표별 경계가 없는 지표(규칙이 없는 지표 등)는 rateCuts(공통 경계)를 쓴다.
 *
 * <h3>품질 정규화</h3>
 * 품질은 앱이 150자 덩어리마다 재서 평균한 값(문단 길이와 무관)이다. 앱에 포함된 품질 모델로 실제 110건을 재면
 * 문서 단위로 p10=0.60, 중앙 0.68, p90=0.73 이고 지표 단위로는 p10=0.57, p90=0.77 이다.
 * qualityFloor 이하를 1점, qualityCeil 이상을 5점으로 늘려 쓴다. 이전 경계(floor 0.10, ceil 0.32)로는 실제 문서 대부분이
 * 천장을 넘어 품질이 구분 기준이 되지 못했고, 정규화 없이 1 + 4 × 품질을 써도 실제 문서가 3.3~4.1 점대에 몰린다.
 *
 * <h3>품질 게이트</h3>
 * 내용 없는 표현("성실함", "원만함" 등)은 많이 반복되어도 밀도 단계만 올려 수준이 높아지는 문제가 있다.
 * 그래서 밀도 단계가 1을 넘는 부분(단계 - 1)에 품질 배율을 곱한다. 배율은 정규화한 품질 q 에 대해
 * rateGateFloor + (1 - rateGateFloor) × q 이다. 품질이 천장이면 배율 1(밀도 단계 그대로),
 * 품질이 바닥이면 배율 rateGateFloor 다. 1 이면 게이트를 쓰지 않는다.
 *
 * 경계값은 실제 생기부 110건(충북 사례집)의 분포에 맞춘 값이다. 같은 값으로 서울대 합격 사례 72건을 재면 서류 수준 중앙이
 * 충북보다 약 0.5 높게 나온다(충북 3.0, 서울대 3.5). 사람이 매긴 서류 수준으로 검증하기 전까지는 그 점을 감안한다.
 *
 * @param rateCuts       공통 신호 밀도(1000자당) 경계(오름차순, 길이 4). 지표별 경계가 없을 때 쓴다.
 * @param indicatorCuts  지표 ID → 그 지표의 신호 밀도 경계(오름차순, 길이 4)
 * @param rateWeight     신호 밀도 단계의 반영 비율
 * @param qualityWeight  품질의 반영 비율. rateWeight 와 합하여 1이다.
 * @param qualityFloor   품질을 1점으로 보는 값(이하)
 * @param qualityCeil    품질을 5점으로 보는 값(이상). qualityFloor 보다 크다.
 * @param rateGateFloor  품질이 바닥일 때 밀도 단계(1 초과분)에 곱하는 배율(0 초과 1 이하). 1 이면 게이트를 쓰지 않는다.
 */
public record IndicatorLevelParameters(
		double[] rateCuts, Map<String, double[]> indicatorCuts, double rateWeight, double qualityWeight,
		double qualityFloor, double qualityCeil, double rateGateFloor) {

	public static final IndicatorLevelParameters DEFAULT = new IndicatorLevelParameters(
			new double[] {0.7, 1.2, 1.7, 2.5},
			Map.of(
				"A2", new double[] {0.18, 0.55, 1.13, 1.56},
				"A3", new double[] {1.44, 2.46, 4.05, 5.09},
				"B3", new double[] {0.79, 1.68, 2.91, 3.73},
				"C1", new double[] {0.74, 1.26, 2.13, 2.70},
				"C2", new double[] {0.31, 0.49, 1.21, 1.81},
				"C3", new double[] {0.22, 0.56, 1.01, 1.50},
				"C4", new double[] {0.15, 0.36, 1.04, 1.47},
				"D1", new double[] {0.16, 0.40, 0.88, 1.38},
				"D3", new double[] {0.42, 0.81, 1.45, 1.88},
				"D4", new double[] {0.15, 0.34, 0.79, 1.09}),
			0.5, 0.5, 0.45, 0.80, 0.3);

	/** 지표별 경계와 품질 정규화 없이 공통 경계만 쓰는 설정(품질은 0~1 을 그대로 1~5 로 편다). */
	public IndicatorLevelParameters(double[] rateCuts, double rateWeight, double qualityWeight) {
		this(rateCuts, Map.of(), rateWeight, qualityWeight, 0.0, 1.0, 1.0);
	}

	/** 품질 게이트 없이(배율 1) 지표별 경계와 품질 정규화만 쓰는 설정. */
	public IndicatorLevelParameters(
			double[] rateCuts, Map<String, double[]> indicatorCuts, double rateWeight, double qualityWeight,
			double qualityFloor, double qualityCeil) {
		this(rateCuts, indicatorCuts, rateWeight, qualityWeight, qualityFloor, qualityCeil, 1.0);
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
		if (rateGateFloor <= 0 || rateGateFloor > 1) {
			throw new IllegalArgumentException("rateGateFloor must be in (0, 1]");
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
		double gatedStage = 1 + (rateStage - 1) * (rateGateFloor + (1 - rateGateFloor) * q);
		double level = rateWeight * gatedStage + qualityWeight * (1 + 4 * q);
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
