package com.fivegrade.backend.recommendation.scale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 9등급과 5등급 사이 누적 비율 환산을 검증한다.
 *
 * 기준 값은 같은 계산을 NumPy의 interp로 한 결과다.
 * DB나 Spring 없이 실행한다.
 */
class GradeScaleConverterTest {

	private static final double DELTA = 1e-9;

	private final GradeScaleConverter converter = new GradeScaleConverter();

	@Test
	void convertsNineGradeToFiveGrade() {
		assertEquals(1.1470588235294117, converter.toFiveGrade(2.0), DELTA);
		assertEquals(1.4264705882352942, converter.toFiveGrade(2.5), DELTA);
		assertEquals(1.7058823529411764, converter.toFiveGrade(3.0), DELTA);
		assertEquals(3.0, converter.toFiveGrade(5.0), DELTA);
		assertEquals(4.294117647058823, converter.toFiveGrade(7.0), DELTA);
		assertEquals(4.852941176470588, converter.toFiveGrade(8.0), DELTA);
	}

	@Test
	void saturatesAtBothEnds() {
		// 9등급 1등급 대표 위치(2.0%)가 5등급 1등급 대표 위치(5.0%)보다 위라서 위쪽이 같은 값으로 모인다.
		assertEquals(1.0, converter.toFiveGrade(1.0), DELTA);
		assertEquals(1.0, converter.toFiveGrade(1.5), DELTA);
		assertTrue(converter.toFiveGrade(1.6) > 1.0);
		assertEquals(5.0, converter.toFiveGrade(8.5), DELTA);
		assertEquals(5.0, converter.toFiveGrade(9.0), DELTA);
		assertTrue(converter.toFiveGrade(8.4) < 5.0);
	}

	@Test
	void convertsFiveGradeToNineGradeEquivalent() {
		assertEquals(1.5454545454545454, converter.toNineGradeEquivalent(1.0), DELTA);
		assertEquals(3.344827586206897, converter.toNineGradeEquivalent(2.0), DELTA);
		assertEquals(5.0, converter.toNineGradeEquivalent(3.0), DELTA);
		assertEquals(6.655172413793103, converter.toNineGradeEquivalent(4.0), DELTA);
		assertEquals(8.454545454545455, converter.toNineGradeEquivalent(5.0), DELTA);
		assertEquals(4.0464864864864865, converter.toNineGradeEquivalent(2.37), DELTA);
	}

	@Test
	void roundTripsInsideTheUnsaturatedRange() {
		for (double nine = 1.6; nine <= 8.4; nine += 0.1) {
			double back = converter.toNineGradeEquivalent(converter.toFiveGrade(nine));
			assertEquals(nine, back, 1e-9);
		}
	}

	@Test
	void isMonotonic() {
		double previous = converter.toFiveGrade(1.0);
		for (double nine = 1.05; nine <= 9.0; nine += 0.05) {
			double current = converter.toFiveGrade(nine);
			assertTrue(current >= previous);
			previous = current;
		}
	}

	@Test
	void rejectsOutOfRangeAndNaN() {
		assertThrows(IllegalArgumentException.class, () -> converter.toFiveGrade(0.99));
		assertThrows(IllegalArgumentException.class, () -> converter.toFiveGrade(9.01));
		assertThrows(IllegalArgumentException.class, () -> converter.toFiveGrade(Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> converter.toNineGradeEquivalent(0.99));
		assertThrows(IllegalArgumentException.class, () -> converter.toNineGradeEquivalent(5.01));
	}
}
