package com.fivegrade.backend.recommendation.probability;

/**
 * 과거 입결로 만든 한 모집단위의 예측 컷.
 *
 * @param high  예측한 70%컷 쪽 경계(큰 값). 합격 가능 풀의 바깥 경계다.
 * @param width 예측한 두 컷의 폭(high - low). 폭이 클수록 불확실성이 커진다.
 * @param years 예측에 쓴 정상 컷 연수. 0이면 정상 컷이 없어 불량 행으로 대신한 것이다.
 */
public record CutForecast(double high, double width, int years) {

	/** 정상 컷이 없어 가장 최근 불량 행으로 대신한 예측인지 여부. */
	public boolean isFallback() {
		return years == 0;
	}
}
