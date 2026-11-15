package com.fivegrade.backend.recommendation.probability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 과거 입결로 만드는 예측 컷의 가중 평균, 정상 행 판정, 불량 행 대체를 검증한다.
 *
 * DB나 Spring 없이 실행한다.
 */
class CutForecasterTest {

	private static final double DELTA = 1e-9;
	private static final List<Integer> RECENT = List.of(2026, 2025, 2024);

	private final CutForecaster forecaster = new CutForecaster();

	@Test
	void weightsThreeRecentYearsByHalfThirdAndFifth() {
		List<YearlyCut> cuts = List.of(
				new YearlyCut(2026, 2.0, 3.0),
				new YearlyCut(2025, 2.2, 3.2),
				new YearlyCut(2024, 2.4, 3.4)
		);

		CutForecast forecast = forecaster.forecast(cuts, RECENT, TrackKind.SUBJECT).orElseThrow();

		// 3.0*0.5 + 3.2*0.3 + 3.4*0.2 = 3.14, 낮은 쪽은 2.14이므로 폭은 1.0
		assertEquals(3.14, forecast.high(), DELTA);
		assertEquals(1.0, forecast.width(), DELTA);
		assertEquals(3, forecast.years());
		assertFalse(forecast.isFallback());
	}

	@Test
	void renormalizesWeightsWhenAYearIsMissing() {
		List<YearlyCut> cuts = List.of(
				new YearlyCut(2025, 2.5, 3.0),
				new YearlyCut(2024, 3.5, 4.0)
		);

		CutForecast forecast = forecaster.forecast(cuts, RECENT, TrackKind.SUBJECT).orElseThrow();

		// 2025와 2024만 있으므로 가중치 0.3, 0.2를 합 0.5로 나눈다: (3.0*0.3 + 4.0*0.2) / 0.5
		assertEquals(3.4, forecast.high(), DELTA);
		assertEquals(0.5, forecast.width(), DELTA);
		assertEquals(2, forecast.years());
	}

	@Test
	void ignoresYearsOlderThanTheRecentThree() {
		List<YearlyCut> cuts = List.of(
				new YearlyCut(2026, 2.0, 3.0),
				new YearlyCut(2023, 1.0, 1.5)
		);

		CutForecast forecast = forecaster.forecast(cuts, RECENT, TrackKind.SUBJECT).orElseThrow();

		assertEquals(3.0, forecast.high(), DELTA);
		assertEquals(1, forecast.years());
	}

	@Test
	void prefersOlderCleanRowOverNewerUncleanRow() {
		List<YearlyCut> cuts = List.of(
				new YearlyCut(2026, 2.0, 4.0),
				new YearlyCut(2025, 2.0, 3.0)
		);

		CutForecast forecast = forecaster.forecast(cuts, RECENT, TrackKind.SUBJECT).orElseThrow();

		assertEquals(3.0, forecast.high(), DELTA);
		assertEquals(1.0, forecast.width(), DELTA);
		assertEquals(1, forecast.years());
	}

	@Test
	void appliesStricterWidthLimitToHolisticTrack() {
		List<YearlyCut> cuts = List.of(new YearlyCut(2026, 2.0, 3.2));

		CutForecast subject = forecaster.forecast(cuts, RECENT, TrackKind.SUBJECT).orElseThrow();
		CutForecast holistic = forecaster.forecast(cuts, RECENT, TrackKind.HOLISTIC).orElseThrow();

		// 폭 1.2는 교과(한도 1.5)에서는 정상, 학종(한도 1.0)에서는 불량이다.
		assertEquals(1, subject.years());
		assertEquals(0, holistic.years());
	}

	@Test
	void usesLatestUncleanRowWhenNoCleanRowExists() {
		List<YearlyCut> cuts = List.of(
				new YearlyCut(2026, 2.0, 4.0),
				new YearlyCut(2025, 3.0, 2.0)
		);

		CutForecast forecast = forecaster.forecast(cuts, RECENT, TrackKind.SUBJECT).orElseThrow();

		// 2026 행은 폭 2.0 > 1.5, 2025 행은 뒤집힘이라 둘 다 불량이다. 가장 최근인 2026 행을 쓴다.
		assertEquals(4.0, forecast.high(), DELTA);
		assertEquals(2.0, forecast.width(), DELTA);
		assertEquals(0, forecast.years());
		assertTrue(forecast.isFallback());
	}

	@Test
	void treatsInvertedRowAsUnclean() {
		YearlyCut inverted = new YearlyCut(2026, 3.0, 2.0);

		assertTrue(inverted.isInverted());
		assertFalse(inverted.isClean(TrackKind.SUBJECT));
		assertEquals(3.0, inverted.high(), DELTA);
		assertEquals(2.0, inverted.low(), DELTA);
	}

	@Test
	void acceptsWidthExactlyAtTheLimit() {
		assertTrue(new YearlyCut(2026, 2.0, 3.5).isClean(TrackKind.SUBJECT));
		assertTrue(new YearlyCut(2026, 2.0, 3.0).isClean(TrackKind.HOLISTIC));
	}

	@Test
	void acceptsWidthAtTheLimitDespiteFloatingPointError() {
		// 4.4 - 2.9는 double로 1.5000000000000004라서 한도 1.5를 넘는 것으로 보였다.
		assertTrue(new YearlyCut(2026, 2.9, 4.4).isClean(TrackKind.SUBJECT));
		assertTrue(new YearlyCut(2026, 2.1, 3.1).isClean(TrackKind.HOLISTIC));
	}

	@Test
	void stillRejectsWidthClearlyOverTheLimit() {
		assertFalse(new YearlyCut(2026, 2.9, 4.41).isClean(TrackKind.SUBJECT));
		assertFalse(new YearlyCut(2026, 2.1, 3.11).isClean(TrackKind.HOLISTIC));
	}

	@Test
	void usesCleanRowWhenWidthIsExactlyAtTheLimitDespiteFloatingPointError() {
		Optional<CutForecast> forecast = forecaster.forecast(
				List.of(new YearlyCut(2026, 2.9, 4.4)), RECENT, TrackKind.SUBJECT);

		assertTrue(forecast.isPresent());
		assertEquals(1, forecast.get().years());
	}

	@Test
	void returnsEmptyWhenThereAreNoCuts() {
		Optional<CutForecast> forecast = forecaster.forecast(List.of(), RECENT, TrackKind.SUBJECT);

		assertFalse(forecast.isPresent());
	}

	@Test
	void treatsUnknownTrackLabelLikeSubject() {
		assertEquals(TrackKind.SUBJECT, TrackKind.fromLabel("교과"));
		assertEquals(TrackKind.HOLISTIC, TrackKind.fromLabel("학종"));
		assertEquals(TrackKind.OTHER, TrackKind.fromLabel(""));
		assertEquals(TrackKind.OTHER, TrackKind.fromLabel("논술"));
		assertEquals(1.5, TrackKind.OTHER.maxCleanWidth(), DELTA);
	}
}
