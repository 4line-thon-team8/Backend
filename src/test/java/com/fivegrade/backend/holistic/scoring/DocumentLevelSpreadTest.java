package com.fivegrade.backend.holistic.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 문서 수준 분포 보정(앵커 보간)을 확인한다. DB나 Spring 없이 실행한다.
 */
class DocumentLevelSpreadTest {

	private static final double DELTA = 1e-9;

	/** 앵커 {1.612, 1.713, 2.073, 2.355, 2.624, 3.445, 3.668} ↔ 수준 {1.0, 1.5, 2.5, 3.0, 3.5, 4.5, 5.0} */
	private final DocumentLevelSpread spread = DocumentLevelSpread.DEFAULT;

	@Test
	void 앵커_위치는_백분위_수준과_정확히_맞는다() {
		assertEquals(1.0, spread.apply(1.612), DELTA);
		assertEquals(1.5, spread.apply(1.713), DELTA);
		assertEquals(2.5, spread.apply(2.073), DELTA);
		assertEquals(3.0, spread.apply(2.355), DELTA);
		assertEquals(3.5, spread.apply(2.624), DELTA);
		assertEquals(4.5, spread.apply(3.445), DELTA);
		assertEquals(5.0, spread.apply(3.668), DELTA);
	}

	@Test
	void 앵커_사이는_선형_보간하고_양끝_바깥은_1과_5로_막는다() {
		// 2.827(2.5)과 3.128(3.0)의 한가운데
		assertEquals(2.75, spread.apply((2.827 + 3.128) / 2), DELTA);
		assertEquals(1.0, spread.apply(1.0), DELTA);
		assertEquals(1.0, spread.apply(2.0), DELTA);
		assertEquals(5.0, spread.apply(4.5), DELTA);
		assertEquals(5.0, spread.apply(5.0), DELTA);
	}

	@Test
	void 보정은_단조_증가라_순서를_바꾸지_않는다() {
		double previous = 0;
		for (double raw = 1.0; raw <= 5.0; raw += 0.01) {
			double level = spread.apply(raw);
			assertTrue(level >= previous, "raw=" + raw);
			previous = level;
		}
	}

	@Test
	void 원_수준의_좁은_구간을_1에서_5로_넓힌다() {
		// 원 수준 2.4~3.5(1.1 폭)가 보정 후 1.3~3.6 (2.3 폭 이상)으로 벌어진다.
		assertTrue(spread.apply(3.5) - spread.apply(2.4) > 2.0);
		// 원 수준 0.1 차이가 중앙 부근에서 0.18 이상의 차이가 된다.
		assertTrue(spread.apply(3.2) - spread.apply(3.1) > 0.15);
	}

	@Test
	void 항등_설정은_원_수준을_그대로_돌려준다() {
		assertEquals(2.37, DocumentLevelSpread.IDENTITY.apply(2.37), DELTA);
		assertEquals(5.0, DocumentLevelSpread.IDENTITY.apply(5.0), DELTA);
	}

	@Test
	void 잘못된_앵커는_거부한다() {
		assertThrowsIllegal(() -> new DocumentLevelSpread(new double[] {1, 2, 3}));
		assertThrowsIllegal(() -> new DocumentLevelSpread(new double[] {1, 2, 3, 3, 5, 6, 7}));
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
