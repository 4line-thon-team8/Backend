package com.fivegrade.backend.holistic.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.holistic.scoring.ElementLevelCalculator.Observation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 지표 신호 → 지표 수준 → 요소 수준 계산을 확인한다. DB나 Spring 없이 실행한다.
 */
class ElementLevelCalculatorTest {

	private static final double DELTA = 1e-9;

	/** 규칙이 있는 지표: 탐구력(학업), 진로 탐색(전공 적합), 협업(공동체), 심화·확장(발전가능성) */
	private static final Map<String, String> OBSERVABLE = Map.of(
			"A3", "ACADEMIC", "B3", "MAJOR_FIT", "C1", "COMMUNITY", "D4", "POTENTIAL");

	private final ElementLevelCalculator calculator = new ElementLevelCalculator();

	/** 서술 10,000자 기준: 신호 1건 = 1000자당 0.1건 */
	private static final long CHARS = 10_000;

	@Test
	void 신호_밀도는_경계에_따라_1에서_5단계가_된다() {
		IndicatorLevelParameters parameters = IndicatorLevelParameters.DEFAULT;

		// 10,000자에서 신호 n건 = 1000자당 n/10건. 경계 {0.7, 1.2, 1.7, 2.5}
		assertEquals(1, parameters.rateLevel(0, CHARS));
		assertEquals(1, parameters.rateLevel(6, CHARS));   // 0.6
		assertEquals(2, parameters.rateLevel(7, CHARS));   // 0.7 (경계 이상)
		assertEquals(2, parameters.rateLevel(11, CHARS));  // 1.1
		assertEquals(3, parameters.rateLevel(12, CHARS));  // 1.2
		assertEquals(3, parameters.rateLevel(16, CHARS));  // 1.6
		assertEquals(4, parameters.rateLevel(17, CHARS));  // 1.7
		assertEquals(4, parameters.rateLevel(24, CHARS));  // 2.4
		assertEquals(5, parameters.rateLevel(25, CHARS));  // 2.5
		assertEquals(5, parameters.rateLevel(500, CHARS));
	}

	@Test
	void 같은_신호_수라도_서술이_길면_밀도가_낮아져_단계가_내려간다() {
		IndicatorLevelParameters parameters = IndicatorLevelParameters.DEFAULT;

		assertEquals(5, parameters.rateLevel(9, 3_000));   // 3.0
		assertEquals(2, parameters.rateLevel(9, 10_000));  // 0.9
		assertEquals(1, parameters.rateLevel(9, 50_000));  // 0.18
		assertTrue(parameters.level(9, 3_000, 0.5) > parameters.level(9, 50_000, 0.5));
	}

	@Test
	void 글자_수가_0_이하여도_계산이_깨지지_않는다() {
		IndicatorLevelParameters parameters = IndicatorLevelParameters.DEFAULT;

		assertEquals(5, parameters.rateLevel(1, 0));
		assertEquals(1, parameters.rateLevel(0, 0));
		assertEquals(5, parameters.rateLevel(1, -5));
	}

	@Test
	void 지표_수준은_신호_밀도와_품질을_섞고_1에서_5_안에_있다() {
		IndicatorLevelParameters parameters = IndicatorLevelParameters.DEFAULT;

		// 밀도 5단계 + 품질 1.0(5점) → 5
		assertEquals(5.0, parameters.level(25, CHARS, 1.0), DELTA);
		// 밀도 1단계 + 품질 0(1점) → 0.6*1 + 0.4*1 = 1.0
		assertEquals(1.0, parameters.level(1, CHARS, 0.0), DELTA);
		// 밀도 2단계 + 품질 0(1점) → 0.6*2 + 0.4*1 = 1.6
		assertEquals(1.6, parameters.level(7, CHARS, 0.0), DELTA);
		// 범위를 벗어난 품질은 0~1 로 맞춘다.
		assertEquals(parameters.level(3, CHARS, 1.0), parameters.level(3, CHARS, 9.0), DELTA);
		assertEquals(parameters.level(3, CHARS, 0.0), parameters.level(3, CHARS, -3.0), DELTA);
	}

	@Test
	void 신호가_많고_품질이_높을수록_수준이_올라간다() {
		IndicatorLevelParameters parameters = IndicatorLevelParameters.DEFAULT;
		double previous = 0;
		for (int count = 0; count <= 30; count++) {
			double level = parameters.level(count, CHARS, 0.5);
			assertTrue(level >= previous);
			previous = level;
		}
		assertTrue(parameters.level(3, CHARS, 0.9) > parameters.level(3, CHARS, 0.1));
	}

	@Test
	void 신호가_전혀_없으면_지표와_요소_수준이_모두_비어_있다() {
		ElementLevelCalculator.Result result = calculator.compute(OBSERVABLE, List.of(), CHARS);

		assertTrue(result.indicatorLevels().isEmpty());
		assertTrue(result.elementLevels().isEmpty());
		assertNull(result.potential());
	}

	@Test
	void 규칙이_있어도_신호가_없는_지표는_평균에서_제외한다() {
		Map<String, String> observable = Map.of("A2", "ACADEMIC", "A3", "ACADEMIC");
		ElementLevelCalculator.Result result = calculator.compute(observable, List.of(
				new Observation("A3", 25, 1.0)), CHARS);

		// A2는 관찰되지 않았을 뿐이므로 A3(5.0)만 평균에 들어간다.
		assertEquals(5.0, result.elementLevels().get("ACADEMIC"), DELTA);
		assertFalse(result.indicatorLevels().containsKey("A2"));
	}

	@Test
	void 규칙이_없는_지표는_측정하지_못한_것이라_수준에_넣지_않는다() {
		// 학업 요소에 지표 A2(규칙 없음)와 A3(규칙 있음)이 있어도 A3만 평균에 들어간다.
		Map<String, String> observable = Map.of("A3", "ACADEMIC");
		ElementLevelCalculator.Result result = calculator.compute(observable, List.of(
				new Observation("A3", 25, 1.0),
				new Observation("A2", 25, 1.0)), CHARS);

		assertEquals(5.0, result.elementLevels().get("ACADEMIC"), DELTA);
		assertFalse(result.indicatorLevels().containsKey("A2"));
		assertNull(result.elementLevels().get("COMMUNITY"));
	}

	@Test
	void 같은_요소의_관찰된_지표는_평균한다() {
		Map<String, String> observable = Map.of("A2", "ACADEMIC", "A3", "ACADEMIC");
		ElementLevelCalculator.Result result = calculator.compute(observable, List.of(
				new Observation("A3", 25, 1.0), new Observation("A2", 7, 0.0)), CHARS);

		// A3=5.0, A2=1.6 → 평균 3.3
		assertEquals(3.3, result.elementLevels().get("ACADEMIC"), DELTA);
	}

	@Test
	void 신호가_관찰된_지표가_없는_요소는_수준이_없고_3축_수준에도_null이다() {
		Map<String, String> observable = Map.of("A3", "ACADEMIC", "C1", "COMMUNITY");
		ElementLevelCalculator.Result result = calculator.compute(observable, List.of(
				new Observation("A3", 16, 0.5), new Observation("C1", 12, 0.5)), CHARS);

		DocumentLevelCalculator.ElementLevels axes = result.axisLevels();
		assertTrue(axes.academic() != null);
		assertNull(axes.career());
		assertTrue(axes.community() != null);
		assertNull(result.potential());
	}

	@Test
	void 잘못된_설정은_거부한다() {
		boolean rejected = false;
		try {
			new IndicatorLevelParameters(new double[] {0.7, 1.2, 1.2, 2.5}, 0.6, 0.4);
		} catch (IllegalArgumentException e) {
			rejected = true;
		}
		assertTrue(rejected);

		rejected = false;
		try {
			new IndicatorLevelParameters(new double[] {0.7, 1.2, 1.7, 2.5}, 0.7, 0.4);
		} catch (IllegalArgumentException e) {
			rejected = true;
		}
		assertTrue(rejected);
	}

}
