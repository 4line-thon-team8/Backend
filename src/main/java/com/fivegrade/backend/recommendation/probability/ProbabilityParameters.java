package com.fivegrade.backend.recommendation.probability;

/**
 * 합격 가능성 구간 계산에 쓰는 모델 상수.
 *
 * 값은 2023~2026학년도 입결로 "과거 컷으로 예측하고 다음 해 실제 컷으로 채점"하는
 * 시간 분할 백테스트를 돌려 정했다. 해마다 컷이 전반적으로 움직이므로(드리프트)
 * 새 입시 결과가 쌓이면 다시 추정해야 한다.
 *
 * @param driftShift       예측 중심에 더하는 보정값. 음수면 더 우수한 쪽(작은 값)으로 옮긴다.
 * @param widthSigmaFactor 두 컷의 폭 1.0당 σ에 더하는 값
 * @param sigmaNoCut       정상 컷이 없어 불량 행으로 대신할 때의 σ 기본값
 * @param sigmaOneYear     정상 컷이 1개 연도일 때의 σ 기본값
 * @param sigmaTwoYears    정상 컷이 2개 연도일 때의 σ 기본값
 * @param sigmaThreeYears  정상 컷이 3개 연도 이상일 때의 σ 기본값
 * @param safe             안정 구간의 최소 가능성
 * @param match            적정 구간의 최소 가능성
 * @param reach            소신 구간의 최소 가능성. 이보다 낮으면 먼 카드다.
 * @param lowGradeStart    이 등급 이상(숫자가 크거나 같은 쪽)을 하위 성적으로 보고 안정 기준을 엄격히 적용한다.
 * @param lowGradeSafe     하위 성적일 때의 안정 최소 가능성
 * @param lowGradeMinYears 하위 성적일 때 안정으로 보려면 필요한 정상 컷 연수
 * @param overProbability  과잉 판정의 최소 가능성
 * @param overGap          과잉 판정의 최소 여유(예측 중심 - 학생 등급)
 */
public record ProbabilityParameters(
		double driftShift,
		double widthSigmaFactor,
		double sigmaNoCut,
		double sigmaOneYear,
		double sigmaTwoYears,
		double sigmaThreeYears,
		double safe,
		double match,
		double reach,
		double lowGradeStart,
		double lowGradeSafe,
		int lowGradeMinYears,
		double overProbability,
		double overGap
) {

	/** 가장 최근 정상 컷 3개 연도에 주는 가중치(최신 → 과거). 없는 연도는 남은 가중치로 다시 정규화한다. */
	public static final double[] YEAR_WEIGHTS = {0.5, 0.3, 0.2};

	public static final ProbabilityParameters DEFAULT = new ProbabilityParameters(
			-0.05, 0.15, 0.95, 0.61, 0.44, 0.35,
			0.85, 0.45, 0.06,
			5.0, 0.96, 2,
			0.99, 1.5
	);

	/**
	 * 정상 컷 연수에 맞는 σ 기본값. 3년 이상은 3년과 같다.
	 */
	public double baseSigma(int years) {
		if (years <= 0) {
			return sigmaNoCut;
		}
		if (years == 1) {
			return sigmaOneYear;
		}
		if (years == 2) {
			return sigmaTwoYears;
		}
		return sigmaThreeYears;
	}
}
