package com.fivegrade.backend.recommendation.selection;

/**
 * 서류 수준별 학종 카드 상한과 안전판 기준. 컨설턴트 사례(S1·S6·S7·S8·S9·S10·S12·S14·S16·S17·S19)에서 정한
 * 초안이며 검증하지 못한 가설값이다. 한곳에 모아 두고 바꿀 때는 이 record만 고친다.
 *
 * @param highLevel          이 서류 수준 이상이면 높음(상한 highCap)
 * @param upperMidLevel      이 서류 수준 이상이면 중상(상한 upperMidCap)
 * @param midLevel           이 서류 수준 이상이면 중(상한 midCap)
 * @param lowLevel           이 서류 수준 이상이면 낮음(상한 lowCap). 미만이면 신호 부족(상한 minimumCap)
 * @param highCap            서류 높음일 때 학종 카드 상한
 * @param upperMidCap        서류 중상일 때 학종 카드 상한
 * @param midCap             서류 중일 때 학종 카드 상한
 * @param lowCap             서류 낮음일 때 학종 카드 상한
 * @param minimumCap         서류 신호 부족일 때 학종 카드 상한
 * @param anchorMaxShare     안정 카드 중 안전판으로 보는 서류 비중 상한(0~1). 이보다 낮으면 서류에 기대지 않는 전형이다.
 */
public record TrackMixParameters(
		double highLevel,
		double upperMidLevel,
		double midLevel,
		double lowLevel,
		int highCap,
		int upperMidCap,
		int midCap,
		int lowCap,
		int minimumCap,
		double anchorMaxShare
) {

	public static final TrackMixParameters DEFAULT = new TrackMixParameters(
			4.2, 3.6, 2.8, 2.0,
			6, 4, 3, 2, 1,
			0.5
	);

	/** 서류 수준 m에 맞는 학종 카드 상한 */
	public int holisticCap(double level) {
		if (level >= highLevel) {
			return highCap;
		}
		if (level >= upperMidLevel) {
			return upperMidCap;
		}
		if (level >= midLevel) {
			return midCap;
		}
		if (level >= lowLevel) {
			return lowCap;
		}
		return minimumCap;
	}

}
