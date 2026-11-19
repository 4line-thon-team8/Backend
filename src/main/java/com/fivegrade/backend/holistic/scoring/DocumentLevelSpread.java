package com.fivegrade.backend.holistic.scoring;

/**
 * 지표 수준을 평균해 만든 문서 수준(원 수준)을 실제 문서 분포 위의 위치로 펴 주는 보정.
 *
 * 지표 수준은 개별로는 1~5를 쓰지만 여러 지표(평균 약 8~10개)를 평균하면 서로 상쇄되어 문서 수준이 3점 근처에 몰린다.
 * 실제 생기부 110건(충북 일반고)과 서울대 합격 사례 72건을 합친 문서에서 원 수준(요소 안 지표 가중 평균 기준)은 p5~p95가 2.3~4.2 이다.
 * 이 구간이 1~5 전체를 쓰도록 백분위 앵커를 단계에 대응시켜 선형 보간한다.
 * 보정은 단조 증가라 문서끼리의 순서는 바뀌지 않고, 벌어지는 정도만 달라진다.
 *
 * 앵커는 원 수준 분포의 백분위 {5, 10, 30, 50, 70, 90, 95}이고 각각 {1.0, 1.5, 2.5, 3.0, 3.5, 4.5, 5.0}에 대응한다.
 * 서류 수준 평가 안내서의 비율(5=상위 10%, 4=10~30%, 3=30~70%, 2=하위 10~30%, 1=하위 10%)과 같다.
 * 5% 아래는 1.0, 95% 위는 5.0 으로 막는다.
 *
 * <h3>한계</h3>
 * 앵커는 균등 가중 3축 평균 분포로 정했다. 전형 프로필의 축 가중이 균등이 아니면 분포가 조금 달라지므로 근사다.
 * 점수의 측정 오차는 보정해도 그대로 상대적으로 남는다. 서울대 발췌를 반으로 나눠 잰 값의 일치도(전체 길이 환산 신뢰도 약 0.6)와
 * 앵커를 부트스트랩으로 다시 뽑을 때의 문서별 흔들림(표준편차 평균 0.11)을 보면, 0.1점 단위 차이는 의미 있게 구분되지 않는다.
 *
 * @param anchors 원 수준 앵커(오름차순, 길이 7). null 이면 보정하지 않는다(항등).
 */
public record DocumentLevelSpread(double[] anchors) {

	/** 앵커 7개가 각각 대응하는 보정 후 수준 */
	static final double[] STAGES = {1.0, 1.5, 2.5, 3.0, 3.5, 4.5, 5.0};

	/** 보정하지 않는 설정. 원 수준을 그대로 돌려준다. */
	public static final DocumentLevelSpread IDENTITY = new DocumentLevelSpread(null);

	/** 충북 110건 + 서울대 72건을 문서마다 같은 비중으로 합친 균등 가중 문서 수준의 백분위 앵커 */
	public static final DocumentLevelSpread DEFAULT = new DocumentLevelSpread(
			new double[] {2.292, 2.434, 2.827, 3.128, 3.422, 3.966, 4.158});

	public DocumentLevelSpread {
		if (anchors != null) {
			if (anchors.length != STAGES.length) {
				throw new IllegalArgumentException("anchors must have " + STAGES.length + " items");
			}
			for (int i = 1; i < anchors.length; i++) {
				if (anchors[i] <= anchors[i - 1]) {
					throw new IllegalArgumentException("anchors must be increasing");
				}
			}
			anchors = anchors.clone();
		}
	}

	/** 원 문서 수준(1~5)을 보정한 수준(1~5)으로 바꾼다. */
	public double apply(double raw) {
		if (anchors == null) {
			return raw;
		}
		int last = anchors.length - 1;
		if (raw <= anchors[0]) {
			return STAGES[0];
		}
		if (raw >= anchors[last]) {
			return STAGES[last];
		}
		int i = 0;
		while (raw >= anchors[i + 1]) {
			i++;
		}
		double ratio = (raw - anchors[i]) / (anchors[i + 1] - anchors[i]);
		return STAGES[i] + ratio * (STAGES[i + 1] - STAGES[i]);
	}

	@Override
	public double[] anchors() {
		return anchors == null ? null : anchors.clone();
	}
}
