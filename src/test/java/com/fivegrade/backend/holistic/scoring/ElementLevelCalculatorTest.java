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

	/** 공통 경계만 쓰는 설정. 기존 계산 규칙(밀도 단계, 요소 평균)을 확인하는 테스트는 이 설정으로 돌린다. */
	private static final IndicatorLevelParameters COMMON =
			new IndicatorLevelParameters(new double[] {0.7, 1.2, 1.7, 2.5}, 0.6, 0.4);

	private final ElementLevelCalculator calculator = new ElementLevelCalculator(COMMON);

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
		// 밀도 2단계 + 품질 0(1점): 게이트 배율 0.3 → 밀도 단계 1 + (2-1)*0.3 = 1.3 → 0.6*1.3 + 0.4*1 = 1.18
		assertEquals(1.18, parameters.level(7, CHARS, 0.0), DELTA);
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

	@Test
	void 지표별_경계로_같은_밀도라도_지표마다_단계가_다르다() {
		IndicatorLevelParameters parameters = IndicatorLevelParameters.DEFAULT;

		// 1000자당 3.0건: 탐구력(A3)은 실제 문서에서 흔해 낮은 단계, 협업 조율(C2)은 드물어 5단계
		assertEquals(1, parameters.rateLevel("A3", 30, CHARS));
		assertEquals(5, parameters.rateLevel("C2", 30, CHARS));
		// 지표별 경계가 없는 지표는 공통 경계를 쓴다.
		assertEquals(parameters.rateLevel(30, CHARS), parameters.rateLevel("ZZ", 30, CHARS));
	}

	@Test
	void 품질은_바닥과_천장_사이로_늘려서_1에서_5점으로_쓴다() {
		IndicatorLevelParameters parameters = IndicatorLevelParameters.DEFAULT;

		// 밀도 1단계(0.6점) + 품질 바닥 이하(1점 × 0.4) = 1.0, 천장 이상이면 0.6 + 2.0 = 2.6
		assertEquals(1.0, parameters.level("A3", 1, CHARS, 0.05), DELTA);
		assertEquals(2.6, parameters.level("A3", 1, CHARS, 0.9), DELTA);
		// 바닥과 천장의 가운데는 품질 3점
		double middle = (parameters.qualityFloor() + parameters.qualityCeil()) / 2;
		assertEquals(0.6 + 0.4 * 3, parameters.level("A3", 1, CHARS, middle), DELTA);
	}

	@Test
	void 서술이_빽빽해도_실제_문서_분포_안에서만_높은_점수를_받는다() {
		IndicatorLevelParameters parameters = IndicatorLevelParameters.DEFAULT;

		// 실제 문서의 중앙값 밀도(A3 4.6, 품질 중앙 0.22)는 한가운데 점수여야 한다.
		double median = parameters.level("A3", 46, CHARS, 0.22);
		assertTrue(median > 2.0 && median < 4.0, "median=" + median);
		// 중앙값의 2배 밀도에 품질 천장이면 5점
		assertEquals(5.0, parameters.level("A3", 100, CHARS, 0.32), DELTA);
	}

	@Test
	void 품질이_낮으면_밀도가_높아도_수준이_오르지_않는다() {
		IndicatorLevelParameters gated = IndicatorLevelParameters.DEFAULT;
		IndicatorLevelParameters ungated = new IndicatorLevelParameters(
				new double[] {0.7, 1.2, 1.7, 2.5}, Map.of(), 0.6, 0.4, 0.10, 0.32);
		// 밀도 5단계 + 품질 바닥: 게이트 없음 0.6*5 + 0.4*1 = 3.4, 게이트 있음 0.6*(1+4*0.3) + 0.4*1 = 1.72
		assertEquals(3.4, ungated.level(25, CHARS, 0.10), DELTA);
		assertEquals(1.72, gated.level(25, CHARS, 0.10), DELTA);
		// 품질이 천장이면 게이트는 영향이 없다
		assertEquals(5.0, gated.level(25, CHARS, 0.32), DELTA);
		assertEquals(ungated.level(25, CHARS, 0.32), gated.level(25, CHARS, 0.32), DELTA);
		// 품질이 높을수록 같은 밀도에서 수준이 오른다(단조)
		assertTrue(gated.level(25, CHARS, 0.15) < gated.level(25, CHARS, 0.25));
		assertTrue(gated.level(25, CHARS, 0.25) < gated.level(25, CHARS, 0.32));
	}

	@Test
	void 잘못된_게이트_배율은_거부한다() {
		assertThrowsIllegal(() -> new IndicatorLevelParameters(
				new double[] {0.7, 1.2, 1.7, 2.5}, Map.of(), 0.6, 0.4, 0.1, 0.3, 0.0));
		assertThrowsIllegal(() -> new IndicatorLevelParameters(
				new double[] {0.7, 1.2, 1.7, 2.5}, Map.of(), 0.6, 0.4, 0.1, 0.3, 1.1));
	}

	@Test
	void 잘못된_지표별_경계와_품질_범위는_거부한다() {
		assertThrowsIllegal(() -> new IndicatorLevelParameters(
				new double[] {0.7, 1.2, 1.7, 2.5}, Map.of("A3", new double[] {1.0, 2.0, 2.0, 3.0}), 0.6, 0.4, 0.1, 0.3));
		assertThrowsIllegal(() -> new IndicatorLevelParameters(
				new double[] {0.7, 1.2, 1.7, 2.5}, Map.of(), 0.6, 0.4, 0.3, 0.3));
		assertThrowsIllegal(() -> new IndicatorLevelParameters(
				new double[] {0.7, 1.2, 1.7, 2.5}, Map.of(), 0.6, 0.4, -0.1, 0.5));
	}

	private static void assertThrowsIllegal(Runnable action) {
		boolean rejected = false;
		try {
			action.run();
		} catch (IllegalArgumentException e) {
			rejected = true;
		}
		assertTrue(rejected);
	}

}
