package com.fivegrade.backend.holistic.scoring;

import com.fivegrade.backend.recommendation.probability.AdmissionBand;

/**
 * 서류 보정 결과. 선택 이유 설명과 디버깅에 쓴다.
 *
 * @param applied             보정을 실제로 적용했는지. false면 나머지 값은 보정 전 값과 같다.
 * @param gradeShift          유리한 방향이 양수인 등급 보정량(9등급 기준)
 * @param baseBand            보정 전 구간
 * @param adjustedBand        보정 후 구간
 * @param adjustedProbability 보정 후 풀 안에 들 가능성(0~1). 평가 불가면 null
 * @param reason              적용 여부와 이유
 */
public record DocumentAdjustment(
		boolean applied,
		double gradeShift,
		AdmissionBand baseBand,
		AdmissionBand adjustedBand,
		Double adjustedProbability,
		Reason reason
) {

	public enum Reason {
		/** 보정을 적용했다(결과 구간이 같더라도 가능성은 움직였을 수 있다). */
		APPLIED,
		/** 서류 수준을 판단할 수 없어 보정하지 않았다. */
		NO_DOCUMENT_LEVEL,
		/** 평가 불가·먼 카드·과잉 구간은 보정하지 않는다. */
		BAND_NOT_ADJUSTABLE
	}

	public static DocumentAdjustment none(AdmissionBand band, Double probability, Reason reason) {
		return new DocumentAdjustment(false, 0.0, band, band, probability, reason);
	}

	/** 보정으로 구간이 올랐는지 */
	public boolean upgraded() {
		return applied && adjustedBand.ordinal() < baseBand.ordinal();
	}

	/** 보정으로 구간이 내려갔는지 */
	public boolean downgraded() {
		return applied && adjustedBand.ordinal() > baseBand.ordinal();
	}

}
