package com.fivegrade.backend.holistic.scoring;

/**
 * 서류 보정에 쓰는 모델 상수.
 *
 * 이 값들은 서류 점수의 정답 데이터가 없어 백테스트로 검증하지 못한 <b>가설값</b>이다.
 * 컷 폭 분포(학종 중앙값 0.115등급)와 컨설턴트 사례(S1~S19)의 방향만 맞춘 초기값이며,
 * 실사용 피드백이 쌓이면 다시 정한다. 코드에 숫자를 흩어 두지 않고 이 record 한 곳에 모은다.
 *
 * @param upwardLambda     서류 수준이 높을 때(m=5) 컷 분포 σ에 곱하는 최대 상향 계수
 * @param downwardLambda   서류 수준이 낮을 때(m=1) 하향 계수. 추출 실패를 서류 부족으로 오해하지 않도록 상향의 절반으로 둔다.
 * @param neutralLevel     보정이 0인 서류 수준(1~5 척도의 가운데)
 * @param maxLevel         서류 수준 척도의 최댓값
 * @param minLevel         서류 수준 척도의 최솟값
 * @param widthFloor       컷 폭이 이보다 좁아도 이 폭으로 본다(폭이 0에 가까운 전형이 보정을 0으로 만들지 않게)
 * @param widthToSigma     50~70% 컷 간격을 σ로 바꾸는 값. 정규분포에서 두 백분위 간격이 0.524σ이다.
 * @param sigmaDocRatio    서류 불확실성을 컷 σ의 몇 배로 더할지
 * @param shareFullAt      서류 비중(0~1)이 이 값 이상이면 보정을 100% 적용한다.
 * @param unknownShareFactor 서류 비중을 모를 때 쓰는 보정 강도 계수
 * @param maxBandShift     한 후보의 구간이 보정으로 움직일 수 있는 최대 단계 수
 */
public record DocumentAdjustmentParameters(
		double upwardLambda,
		double downwardLambda,
		double neutralLevel,
		double maxLevel,
		double minLevel,
		double widthFloor,
		double widthToSigma,
		double sigmaDocRatio,
		double shareFullAt,
		double unknownShareFactor,
		int maxBandShift
) {

	public static final DocumentAdjustmentParameters DEFAULT = new DocumentAdjustmentParameters(
			0.8, 0.4, 3.0, 5.0, 1.0,
			0.10, 0.524, 0.4,
			0.70, 0.5,
			1
	);

	/** 서류 수준 m(1~5)을 컷 σ 단위 보정 계수로 바꾼다. 3이 0, 5가 +upwardLambda, 1이 -downwardLambda이다. */
	public double lambda(double level) {
		double clamped = Math.max(minLevel, Math.min(maxLevel, level));
		if (clamped >= neutralLevel) {
			return upwardLambda * (clamped - neutralLevel) / (maxLevel - neutralLevel);
		}
		return -downwardLambda * (neutralLevel - clamped) / (neutralLevel - minLevel);
	}

	/** 서류 비중(0~1, 모르면 null)에서 보정 강도 계수(0~1)를 만든다. */
	public double shareFactor(Double documentShare) {
		if (documentShare == null) {
			return unknownShareFactor;
		}
		return Math.max(0.0, Math.min(1.0, documentShare / shareFullAt));
	}

}
