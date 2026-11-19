package com.fivegrade.backend.holistic.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.holistic.report.HolisticStrengthSelector.Strength;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HolisticStrengthSelectorTest {

	private final HolisticStrengthSelector selector = HolisticStrengthSelector.withDefaults();

	@Test
	void HIGH_구간이고_신뢰도가_LOW가_아닌_지표만_강한_순으로_고른다() {
		// A3(MID), A2(MID), C3(MID) 는 후보, D4(LOW 신뢰도)는 높아도 제외
		List<Strength> result = selector.select(Map.of("A3", 4.6, "A2", 3.6, "C3", 4.0, "D4", 5.0));

		assertEquals(List.of("A3", "C3", "A2"), result.stream().map(Strength::indicatorId).toList());
		assertEquals(5, result.getFirst().level());
		assertEquals("탐구력", result.getFirst().label());
	}

	@Test
	void 최대_3개만_내리고_진로_연결_지표가_먼저이며_나머지는_같은_수준이면_지표_ID_순이다() {
		HolisticReportParameters params = new HolisticReportParameters(3.5, 2.5, 3000, Map.of(
				"A2", HolisticReportParameters.Reliability.MID, "A3", HolisticReportParameters.Reliability.MID,
				"C3", HolisticReportParameters.Reliability.MID, "C1", HolisticReportParameters.Reliability.MID));
		List<Strength> result = new HolisticStrengthSelector(params)
				.select(Map.of("C1", 4.0, "A2", 4.0, "A3", 4.0, "C3", 4.0));

		assertEquals(List.of("A3", "A2", "C1"), result.stream().map(Strength::indicatorId).toList());
	}

	@Test
	void 강점이_없으면_빈_목록이다() {
		assertTrue(selector.select(Map.of("A3", 3.0, "A2", 2.0)).isEmpty());
		assertTrue(selector.select(Map.of()).isEmpty());
	}

	@Test
	void 모르는_지표는_무시한다() {
		assertTrue(selector.select(Map.of("Z9", 5.0)).isEmpty());
	}

	@Test
	void 강점_설명은_모든_지표에서_22자_이하다() {
		HolisticReportParameters params = new HolisticReportParameters(3.5, 2.5, 3000, Map.of(
				"A2", HolisticReportParameters.Reliability.MID, "A3", HolisticReportParameters.Reliability.MID,
				"B3", HolisticReportParameters.Reliability.MID, "C1", HolisticReportParameters.Reliability.MID,
				"C3", HolisticReportParameters.Reliability.MID, "D1", HolisticReportParameters.Reliability.MID,
				"D3", HolisticReportParameters.Reliability.MID, "D4", HolisticReportParameters.Reliability.MID));
		HolisticStrengthSelector all = new HolisticStrengthSelector(params);
		for (String id : List.of("A2", "A3", "B3", "C1", "C3", "D1", "D3", "D4")) {
			List<Strength> result = all.select(Map.of(id, 4.5));
			if (!result.isEmpty()) {
				assertTrue(result.getFirst().description().length() <= 22, id + " " + result.getFirst().description());
			}
		}
	}

	@Test
	void 진로_연결_지표는_수준이_더_높은_행동_지표보다_먼저_담는다() {
		// 행동 지표(C1, C3, A2)가 수준 값은 더 높아도 탐구력(A3)이 자리를 먼저 얻는다.
		List<Strength> result = selector.select(Map.of("C1", 4.6, "C3", 4.4, "A2", 4.2, "A3", 3.6));

		assertEquals(List.of("A3", "C1", "C3"), result.stream().map(Strength::indicatorId).toList());
	}

	@Test
	void 진로_연결_지표는_HIGH에_못_미쳐도_중립을_넘으면_후보가_된다() {
		assertEquals(List.of("A3"), selector.select(Map.of("A3", 3.2)).stream().map(Strength::indicatorId).toList());
		assertEquals(List.of("B3", "C1"),
				selector.select(Map.of("B3", 3.3, "C1", 4.0)).stream().map(Strength::indicatorId).toList());
	}

	@Test
	void 진로_연결_지표라도_중립_근처이거나_신뢰도가_낮으면_후보가_아니다() {
		assertTrue(selector.select(Map.of("A3", 3.19, "B3", 3.0)).isEmpty());
		// D3 는 신뢰도 LOW 라 수준이 높아도 제외
		assertTrue(selector.select(Map.of("D3", 4.5)).isEmpty());
	}

	@Test
	void 진로_연결이_아닌_지표는_HIGH가_아니면_후보가_아니다() {
		assertTrue(selector.select(Map.of("C1", 3.4, "C3", 3.3, "A2", 3.2)).isEmpty());
	}
}
