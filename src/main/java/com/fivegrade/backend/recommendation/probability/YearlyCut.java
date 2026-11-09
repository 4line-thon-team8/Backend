package com.fivegrade.backend.recommendation.probability;

/**
 * 한 모집단위의 한 해 입결(50%컷·70%컷).
 *
 * 값은 모두 같은 등급 기준(9등급 환산, 작을수록 우수)이어야 한다.
 * 공시 값이 뒤집혀 있어도(50%컷 > 70%컷) 그대로 받으며, 신뢰할 수 있는지는 isClean으로 판정한다.
 */
public record YearlyCut(int year, double cut50, double cut70) {

	/** 두 컷 중 우수한 쪽(작은 값). */
	public double low() {
		return Math.min(cut50, cut70);
	}

	/** 두 컷 중 낮은 쪽(큰 값). 합격 가능 풀의 바깥 경계로 쓴다. */
	public double high() {
		return Math.max(cut50, cut70);
	}

	public double width() {
		return high() - low();
	}

	/** 50%컷이 70%컷보다 나쁜(큰) 행. 공시 오류이거나 파싱 오류일 가능성이 높다. */
	public boolean isInverted() {
		return cut50 > cut70;
	}

	/**
	 * 예측에 쓸 수 있는 정상 행인지 판정한다.
	 * 뒤집히지 않았고, 두 컷의 폭이 전형 계열의 한도(교과 1.5, 학종 1.0) 이하여야 한다.
	 */
	public boolean isClean(TrackKind track) {
		return !isInverted() && width() <= track.maxCleanWidth();
	}
}
