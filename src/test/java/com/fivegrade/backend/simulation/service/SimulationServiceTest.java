package com.fivegrade.backend.simulation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.conversion.calculation.FiveGradeAverageCalculator;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.RecommendationQuery;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.TrackPreference;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.simulation.dto.ApplicationPreference;
import com.fivegrade.backend.simulation.dto.PreferredTrack;
import com.fivegrade.backend.simulation.dto.SimulationRequest;
import com.fivegrade.backend.simulation.dto.SubjectInput;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 실제 추천 DTO를 사용해 DB·Spring 없이 평균과 추천 호출 경계를 검증한다. */
class SimulationServiceTest {

	private final FiveGradeAverageCalculator calculator = new FiveGradeAverageCalculator();
	private final RecommendationQueryMapper mapper = new DefaultRecommendationQueryMapper();

	@Test
	void passesComparisonAverageAndKeepsDisplayAverageSeparate() {
		RecommendationQuery[] captured = new RecommendationQuery[1];
		RecommendationResult expected = RecommendationResult.empty();
		int[] calls = {0};
		var service = new SimulationService(calculator, mapper, query -> {
			captured[0] = query;
			calls[0]++;
			return expected;
		});

		var analysis = service.analyzeValidated(request(1, 1, 1, 1, 1, 1, 1, 2));

		assertEquals(0, new BigDecimal("1.125").compareTo(captured[0].fiveGradeAverage()));
		assertEquals(new BigDecimal("1.13"), analysis.displayAverage());
		assertEquals(1, calls[0]);
		assertSame(expected, analysis.recommendation());
	}

	@Test
	void keepsDecimal128PrecisionForARecurringAverage() {
		RecommendationQuery[] captured = new RecommendationQuery[1];
		var service = new SimulationService(calculator, mapper, query -> {
			captured[0] = query;
			return RecommendationResult.empty();
		});

		service.analyzeValidated(request(1, 2, 2));

		assertEquals(new BigDecimal("1.666666666666666666666666666666667"),
				captured[0].fiveGradeAverage());
	}

	@Test
	void countsDifferentRowsWithTheSameSubjectCodeInRequestOrder() {
		var service = new SimulationService(calculator, mapper, query -> RecommendationResult.empty());

		var analysis = service.analyzeValidated(request(1, 5));

		assertEquals(new BigDecimal("3.00"), analysis.displayAverage());
		assertEquals(2, analysis.subjects().size());
		assertEquals("row-0", analysis.subjects().get(0).subjectRef());
		assertEquals("row-1", analysis.subjects().get(1).subjectRef());
		assertEquals(new BigDecimal("1.00"), analysis.subjects().get(0).grade());
		assertEquals(new BigDecimal("5.00"), analysis.subjects().get(1).grade());
	}

	@Test
	void mapsDefaultPreferenceValues() {
		var preference = new ApplicationPreference(List.of("MAJOR_TEST"), null, null, null);

		var query = mapper.map(new BigDecimal("2.25"), preference);

		assertEquals(List.of("MAJOR_TEST"), query.desiredMajorCodes());
		assertEquals(TrackPreference.ANY, query.trackPreference());
		assertFalse(query.includeReachCard());
		assertTrue(query.allowSimilarMajors());
	}

	@Test
	void mapsEveryRequestTrackToTheRecommendationEnum() {
		List<PreferredTrack> requestTracks = List.of(
				PreferredTrack.SUBJECT, PreferredTrack.HOLISTIC, PreferredTrack.ANY);
		List<TrackPreference> recommendationTracks = List.of(
				TrackPreference.SUBJECT, TrackPreference.HOLISTIC, TrackPreference.ANY);

		for (int i = 0; i < requestTracks.size(); i++) {
			var preference = new ApplicationPreference(
					List.of("MAJOR_TEST"), requestTracks.get(i), false, true);
			assertEquals(recommendationTracks.get(i),
					mapper.map(new BigDecimal("2.25"), preference).trackPreference());
		}
	}

	@Test
	void mapsExplicitMajorsAndOptionsWithoutChangingTheAverage() {
		var average = new BigDecimal("2.123456789012345678901234567890123");
		var preference = new ApplicationPreference(
				List.of("MAJOR_A", "MAJOR_B"), PreferredTrack.HOLISTIC, true, false);

		var query = mapper.map(average, preference);

		assertSame(average, query.fiveGradeAverage());
		assertEquals(List.of("MAJOR_A", "MAJOR_B"), query.desiredMajorCodes());
		assertTrue(query.includeReachCard());
		assertFalse(query.allowSimilarMajors());
	}

	@Test
	void preservesZeroPartialAndSixCardResultsWithTheirWarnings() {
		for (int count : List.of(0, 1, 5, 6)) {
			RecommendationResult expected = resultWithCards(count);
			var service = new SimulationService(calculator, mapper, query -> expected);

			var actual = service.analyzeValidated(request(3)).recommendation();

			assertSame(expected, actual);
			assertEquals(count, actual.cards().size());
			assertEquals(expected.warnings(), actual.warnings());
			for (int i = 0; i < count; i++) {
				assertEquals(i + 1, actual.cards().get(i).rank());
				assertEquals(0.123456789012345, actual.cards().get(i).gradeGap());
			}
		}
	}

	@Test
	void preservesUnratedNullValuesAndCardWarningRank() {
		var card = new RecommendedCard(1, 101L, "테스트대학", "테스트학과", "교과",
				AdmissionBand.UNRATED, CandidateKind.DESIRED_MAJOR, null,
				new CardEvidence(null, null, null, 0));
		var expected = new RecommendationResult(List.of(card), List.of(
				RecommendationWarning.forResult(WarningCode.FEWER_THAN_SIX_CARDS),
				RecommendationWarning.forCard(WarningCode.UNRATED_CARD, 1)));
		var service = new SimulationService(calculator, mapper, query -> expected);

		var actual = service.analyzeValidated(request(3)).recommendation();

		assertSame(expected, actual);
		assertNull(actual.cards().get(0).gradeGap());
		assertNull(actual.cards().get(0).evidence().quota());
		assertNull(actual.cards().get(0).evidence().competitionRate());
		assertNull(actual.cards().get(0).evidence().newOrMergedDepartment());
		assertNull(actual.warnings().get(0).rank());
		assertEquals(Integer.valueOf(1), actual.warnings().get(1).rank());
	}

	@Test
	void preservesKnownZeroAndFalseEvidenceValues() {
		var card = new RecommendedCard(1, 102L, "테스트대학", "테스트학과", "교과",
				AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR, 0.0,
				new CardEvidence(0, 0.0, false, 3));
		var expected = new RecommendationResult(List.of(card), List.of(
				RecommendationWarning.forResult(WarningCode.FEWER_THAN_SIX_CARDS)));
		var service = new SimulationService(calculator, mapper, query -> expected);

		var actual = service.analyzeValidated(request(3)).recommendation();

		assertEquals(Integer.valueOf(0), actual.cards().get(0).evidence().quota());
		assertEquals(Double.valueOf(0.0), actual.cards().get(0).evidence().competitionRate());
		assertEquals(Boolean.FALSE, actual.cards().get(0).evidence().newOrMergedDepartment());
	}

	@Test
	void propagatesRecommendationFailure() {
		var failure = new IllegalStateException("test failure");
		var service = new SimulationService(calculator, mapper, query -> { throw failure; });

		var actual = assertThrows(IllegalStateException.class,
				() -> service.analyzeValidated(request(3)));

		assertSame(failure, actual);
	}

	@Test
	void rejectsNullResultInsteadOfTurningItIntoAnEmptySuccess() {
		var service = new SimulationService(calculator, mapper, query -> null);
		assertThrows(NullPointerException.class, () -> service.analyzeValidated(request(3)));
	}

	private RecommendationResult resultWithCards(int count) {
		if (count == 0) {
			return RecommendationResult.empty();
		}
		List<RecommendedCard> cards = new ArrayList<>();
		for (int rank = 1; rank <= count; rank++) {
			cards.add(new RecommendedCard(rank, 100L + rank, "테스트대학" + rank, "테스트학과", "교과",
					AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR, 0.123456789012345,
					new CardEvidence(10, 5.5, false, 3)));
		}
		List<RecommendationWarning> warnings = count < 6
				? List.of(RecommendationWarning.forResult(WarningCode.FEWER_THAN_SIX_CARDS))
				: List.of();
		return new RecommendationResult(cards, warnings);
	}

	private SimulationRequest request(int... grades) {
		List<SubjectInput> subjects = new ArrayList<>();
		for (int i = 0; i < grades.length; i++) {
			subjects.add(new SubjectInput("row-" + i, "SUBJ_0001", grades[i]));
		}
		return new SimulationRequest(subjects,
				new ApplicationPreference(List.of("MAJOR_TEST"), null, null, null), true);
	}
}
