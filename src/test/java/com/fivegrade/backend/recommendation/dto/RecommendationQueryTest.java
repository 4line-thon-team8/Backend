package com.fivegrade.backend.recommendation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 추천 서비스 입력의 생성 시점 검증을 확인한다.
 *
 * DB나 Spring 없이 실행한다.
 */
class RecommendationQueryTest {

	private static RecommendationQuery query(BigDecimal average, List<String> majors) {
		return new RecommendationQuery(average, majors, TrackPreference.ANY, false, true);
	}

	@Test
	void keepsTheAverageWithoutRounding() {
		BigDecimal average = new BigDecimal("1.6499999999999999");

		RecommendationQuery query = query(average, List.of("HISTORY"));

		assertEquals(0, average.compareTo(query.fiveGradeAverage()));
	}

	@Test
	void acceptsAverageAtBothEnds() {
		query(new BigDecimal("1.0"), List.of("HISTORY"));
		query(new BigDecimal("5.0"), List.of("HISTORY"));
	}

	@Test
	void rejectsAverageOutOfRangeOrMissing() {
		assertThrows(IllegalArgumentException.class, () -> query(null, List.of("HISTORY")));
		assertThrows(IllegalArgumentException.class, () -> query(new BigDecimal("0.99"), List.of("HISTORY")));
		assertThrows(IllegalArgumentException.class, () -> query(new BigDecimal("5.01"), List.of("HISTORY")));
	}

	@Test
	void rejectsInvalidDesiredMajors() {
		BigDecimal average = new BigDecimal("2.5");

		assertThrows(IllegalArgumentException.class, () -> query(average, null));
		assertThrows(IllegalArgumentException.class, () -> query(average, List.of()));
		assertThrows(IllegalArgumentException.class, () -> query(average, List.of("A", "B", "C", "D")));
		assertThrows(IllegalArgumentException.class, () -> query(average, List.of("HISTORY", "HISTORY")));
		assertThrows(IllegalArgumentException.class, () -> query(average, List.of(" ")));
		assertThrows(IllegalArgumentException.class, () -> query(average, Arrays.asList("HISTORY", null)));
	}

	@Test
	void rejectsMissingTrackPreference() {
		assertThrows(IllegalArgumentException.class, () -> new RecommendationQuery(
				new BigDecimal("2.5"), List.of("HISTORY"), null, false, true));
	}

	@Test
	void doesNotExposeInputValuesInTheMessage() {
		String secretCode = "secret-code-123";

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> query(new BigDecimal("9.87654321"), List.of(secretCode, secretCode)));

		assertFalse(error.getMessage().contains(secretCode));
		assertFalse(error.getMessage().contains("9.87654321"));
	}

	@Test
	void copiesTheMajorListDefensively() {
		List<String> majors = new ArrayList<>(List.of("HISTORY"));
		RecommendationQuery query = query(new BigDecimal("2.5"), majors);

		majors.add("KOREAN_LIT");

		assertEquals(List.of("HISTORY"), query.desiredMajorCodes());
		assertThrows(UnsupportedOperationException.class, () -> query.desiredMajorCodes().add("X"));
		assertTrue(query.allowSimilarMajors());
	}

}
