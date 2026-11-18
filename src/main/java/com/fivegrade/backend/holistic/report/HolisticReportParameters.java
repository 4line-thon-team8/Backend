package com.fivegrade.backend.holistic.report;

import java.util.Map;

/**
 * 리포트에서 쓰는 구간 기준과 지표별 모델 신뢰도. 검증 전 가설 값이므로 이 한 곳에서만 정한다.
 *
 * 구간: 수준이 highFrom 이상이면 HIGH, lowUntil 이하이면 LOW, 그 사이는 MID 다({@code DocumentFit}과 같은 방식).
 * 신뢰도: 서술 분석 모델 0.4.0을 처음 보는 문서(N01)에서 잰 지표별 F1 이 0.60 이상이면 MID, 미만이면 LOW 다.
 * 아직 HIGH 인 지표는 없다. 새 모델·새 정답으로 다시 잰 뒤 이 표를 갱신한다.
 *
 * @param highFrom         이 수준 이상이면 HIGH
 * @param lowUntil         이 수준 이하이면 LOW
 * @param limitedBelowChars 읽은 서술 글자 수가 이 값 미만이면 서술 분량이 적다고 본다
 * @param reliability      지표 ID → 모델 신뢰도. 표에 없는 지표는 LOW 로 본다.
 */
public record HolisticReportParameters(
		double highFrom,
		double lowUntil,
		int limitedBelowChars,
		Map<String, Reliability> reliability
) {

	/** 지표 판별 모델의 신뢰도 */
	public enum Reliability {
		HIGH, MID, LOW
	}

	public static final HolisticReportParameters DEFAULT = new HolisticReportParameters(
			3.5, 2.5, 3000,
			Map.ofEntries(
					Map.entry("A2", Reliability.MID),
					Map.entry("A3", Reliability.MID),
					Map.entry("B3", Reliability.LOW),
					Map.entry("C1", Reliability.LOW),
					Map.entry("C2", Reliability.LOW),
					Map.entry("C3", Reliability.MID),
					Map.entry("C4", Reliability.LOW),
					Map.entry("D1", Reliability.LOW),
					Map.entry("D3", Reliability.LOW),
					Map.entry("D4", Reliability.LOW)));

	public HolisticReportParameters {
		if (lowUntil >= highFrom) {
			throw new IllegalArgumentException("lowUntil must be less than highFrom");
		}
		if (limitedBelowChars < 0) {
			throw new IllegalArgumentException("limitedBelowChars must not be negative");
		}
		reliability = Map.copyOf(reliability);
	}

	public ReportBand bandOf(double level) {
		if (level >= highFrom) {
			return ReportBand.HIGH;
		}
		if (level <= lowUntil) {
			return ReportBand.LOW;
		}
		return ReportBand.MID;
	}

	/** 화면의 막대 등에 쓰는 1~5 정수 수준 */
	public int displayLevel(double level) {
		return (int) Math.min(5, Math.max(1, Math.round(level)));
	}

	public Reliability reliabilityOf(String indicatorId) {
		return reliability.getOrDefault(indicatorId, Reliability.LOW);
	}
}
