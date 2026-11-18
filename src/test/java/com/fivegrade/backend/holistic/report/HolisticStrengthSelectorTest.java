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
	void 최대_3개만_내리고_같은_수준이면_지표_ID_순이다() {
		HolisticReportParameters params = new HolisticReportParameters(3.5, 2.5, 3000, Map.of(
				"A2", HolisticReportParameters.Reliability.MID, "A3", HolisticReportParameters.Reliability.MID,
				"C3", HolisticReportParameters.Reliability.MID, "C1", HolisticReportParameters.Reliability.MID));
		List<Strength> result = new HolisticStrengthSelector(params)
				.select(Map.of("C1", 4.0, "A2", 4.0, "A3", 4.0, "C3", 4.0));

		assertEquals(List.of("A2", "A3", "C1"), result.stream().map(Strength::indicatorId).toList());
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
}
