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
 * <h3>연속 단계(앵커)</h3>
 * 경계로 나눈 5단계는 계단식이라, 경계를 막 넘은 문서와 막 못 넘은 문서가 한 단계씩 차이 나고 같은 단계 안의 차이는 보이지 않는다.
 * 지표를 평균하면 이 계단이 서로 상쇄되어 문서 수준이 3점 근처 좁은 구간에 몰린다. 그래서 지표별 앵커가 있으면
 * 밀도를 앵커 사이에서 선형 보간해 1.0~5.0 연속 단계로 쓴다. 앵커는 실제 문서 분포의 백분위 {5, 10, 30, 50, 70, 90, 95}이고
 * 각각 단계 {1.0, 1.5, 2.5, 3.0, 3.5, 4.5, 5.0}에 대응한다(계단식 경계 10·30·70·90은 그대로 1.5·2.5·3.5·4.5의 지점이다).
 * 5% 아래는 1.0, 95% 위는 5.0 으로 막는다. 앵커가 없는 지표는 위 계단식 경계(정수 단계)를 쓴다.
 *
 * <h3>자료</h3>
 * 경계와 앵커는 충북 일반고 사례집 110건과 서울대 합격 사례 72건(2019~2026)을 같은 분석 파이프라인으로 재서, 문서마다 같은 비중으로
 * 합친 분포의 백분위다. 서울대 사례는 합격자 발췌라 충북보다 분포가 위쪽이므로 합치면 앵커가 위로 올라간다.
 * 충북만으로 재면 서울대 중앙이 4.9 까지 올라가 서울대 사례끼리 구분이 사라지고, 서울대만으로 재면 충북 중앙이 2.6 이 된다.
 * 사람이 매긴 서류 수준으로 검증하기 전까지는 이 점을 감안한다.
 *
 * @param rateCuts       공통 신호 밀도(1000자당) 경계(오름차순, 길이 4). 지표별 경계가 없을 때 쓴다.
 * @param indicatorCuts  지표 ID → 그 지표의 신호 밀도 경계(오름차순, 길이 4)
 * @param rateWeight     신호 밀도 단계의 반영 비율
 * @param qualityWeight  품질의 반영 비율. rateWeight 와 합하여 1이다.
 * @param qualityFloor   품질을 1점으로 보는 값(이하)
 * @param qualityCeil    품질을 5점으로 보는 값(이상). qualityFloor 보다 크다.
 * @param rateGateFloor  품질이 바닥일 때 밀도 단계(1 초과분)에 곱하는 배율(0 초과 1 이하). 1 이면 게이트를 쓰지 않는다.
 * @param indicatorAnchors 지표 ID → 그 지표의 밀도 앵커(오름차순, 길이 7, 백분위 5·10·30·50·70·90·95). 없으면 계단식 단계를 쓴다.
 */
public record IndicatorLevelParameters(
		double[] rateCuts, Map<String, double[]> indicatorCuts, double rateWeight, double qualityWeight,
		double qualityFloor, double qualityCeil, double rateGateFloor, Map<String, double[]> indicatorAnchors) {

	/** 앵커 7개가 각각 대응하는 연속 단계. 백분위 5·10·30·50·70·90·95 에 해당한다. */
	static final double[] ANCHOR_STAGES = {1.0, 1.5, 2.5, 3.0, 3.5, 4.5, 5.0};

	public static final IndicatorLevelParameters DEFAULT = new IndicatorLevelParameters(
			new double[] {0.7, 1.2, 1.7, 2.5},
			Map.of(
				"A2", new double[] {0.30, 0.61, 1.31, 2.11},
				"A3", new double[] {1.59, 2.79, 4.69, 7.15},
				"B3", new double[] {0.40, 0.93, 2.57, 3.41},
				"C1", new double[] {0.61, 1.14, 2.03, 2.86},
				"C2", new double[] {0.32, 0.52, 1.12, 1.79},
				"C3", new double[] {0.30, 0.53, 1.05, 1.51},
				"C4", new double[] {0.18, 0.49, 1.04, 1.59},
				"D1", new double[] {0.29, 0.42, 0.91, 1.35},
				"D3", new double[] {0.42, 0.70, 1.52, 1.96},
				"D4", new double[] {0.28, 0.48, 0.99, 1.94}),
			0.5, 0.5, 0.65, 0.98, 0.3,
			Map.of(
				"A2", new double[] {0.19, 0.30, 0.61, 0.94, 1.31, 2.11, 2.86},
				"A3", new double[] {1.17, 1.59, 2.79, 3.58, 4.69, 7.15, 9.03},
				"B3", new double[] {0.33, 0.40, 0.93, 1.83, 2.57, 3.41, 3.85},
				"C1", new double[] {0.50, 0.61, 1.14, 1.63, 2.03, 2.86, 3.43},
				"C2", new double[] {0.30, 0.32, 0.52, 0.71, 1.12, 1.79, 1.93},
				"C3", new double[] {0.18, 0.30, 0.53, 0.73, 1.05, 1.51, 1.92},
				"C4", new double[] {0.16, 0.18, 0.49, 0.71, 1.04, 1.59, 1.89},
				"D1", new double[] {0.21, 0.29, 0.42, 0.62, 0.91, 1.35, 1.53},
				"D3", new double[] {0.31, 0.42, 0.70, 1.08, 1.52, 1.96, 2.51},
				"D4", new double[] {0.16, 0.28, 0.48, 0.71, 0.99, 1.94, 2.39}));

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

	/** 지표별 경계와 품질 게이트까지 쓰되, 밀도는 계단식(정수) 단계로만 보는 설정. 연속 단계 앵커가 없다. */
	public IndicatorLevelParameters(
			double[] rateCuts, Map<String, double[]> indicatorCuts, double rateWeight, double qualityWeight,
			double qualityFloor, double qualityCeil, double rateGateFloor) {
		this(rateCuts, indicatorCuts, rateWeight, qualityWeight, qualityFloor, qualityCeil, rateGateFloor, Map.of());
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
		if (indicatorAnchors == null) {
			throw new IllegalArgumentException("indicatorAnchors must not be null");
		}
		Map<String, double[]> anchorCopy = new HashMap<>();
		indicatorAnchors.forEach((id, anchors) -> {
			checkAnchors(anchors, "indicatorAnchors[" + id + "]");
			anchorCopy.put(id, anchors.clone());
		});
		rateCuts = rateCuts.clone();
		indicatorCuts = Map.copyOf(copy);
		indicatorAnchors = Map.copyOf(anchorCopy);
	}

	private static void checkAnchors(double[] anchors, String name) {
		if (anchors == null || anchors.length != ANCHOR_STAGES.length) {
			throw new IllegalArgumentException(name + " must have " + ANCHOR_STAGES.length + " items");
		}
		if (anchors[0] <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		for (int i = 1; i < anchors.length; i++) {
			if (anchors[i] <= anchors[i - 1]) {
				throw new IllegalArgumentException(name + " must be increasing");
			}
		}
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

	/**
	 * 그 지표의 밀도 단계를 1.0~5.0 연속 값으로 만든다. 지표별 앵커가 있으면 앵커 사이를 선형 보간하고(5% 아래는 1.0,
	 * 95% 위는 5.0), 없으면 {@link #rateLevel(String, int, long)}의 정수 단계를 그대로 쓴다.
	 */
	public double rateStage(String indicatorId, int count, long narrativeChars) {
		double[] anchors = indicatorAnchors.get(indicatorId);
		if (anchors == null) {
			return rateLevel(indicatorId, count, narrativeChars);
		}
		double rate = count * 1000.0 / Math.max(1L, narrativeChars);
		if (rate <= anchors[0]) {
			return ANCHOR_STAGES[0];
		}
		int last = anchors.length - 1;
		if (rate >= anchors[last]) {
			return ANCHOR_STAGES[last];
		}
		int i = 0;
		while (rate >= anchors[i + 1]) {
			i++;
		}
		double ratio = (rate - anchors[i]) / (anchors[i + 1] - anchors[i]);
		return ANCHOR_STAGES[i] + ratio * (ANCHOR_STAGES[i + 1] - ANCHOR_STAGES[i]);
	}

	/** 신호 수·서술 글자 수·품질(0~1)로 지표 수준(1~5)을 만든다(공통 경계). */
	public double level(int count, long narrativeChars, double quality) {
		return combine(rateLevel(count, narrativeChars), quality);
	}

	/** 지표별 밀도 단계(앵커가 있으면 연속, 없으면 계단식)로 지표 수준(1~5)을 만든다. */
	public double level(String indicatorId, int count, long narrativeChars, double quality) {
		return combine(rateStage(indicatorId, count, narrativeChars), quality);
	}

	private double combine(double rateStage, double quality) {
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

	@Override
	public Map<String, double[]> indicatorAnchors() {
		Map<String, double[]> copy = new HashMap<>();
		indicatorAnchors.forEach((id, anchors) -> copy.put(id, anchors.clone()));
		return copy;
	}
}
