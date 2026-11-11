package com.fivegrade.backend.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.recommendation.candidate.EligibilityRestrictions;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.RecommendationQuery;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.TrackPreference;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 적재된 전형 DB(V5~V8 시드)로 추천 서비스 전체 흐름을 확인한다.
 * 시드 값 하나하나가 아니라 결과가 지켜야 하는 규칙(6장, 대학당 제한, 자격 제한 전형 제외 등)을 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DefaultRecommendationServiceTest {

	@Autowired
	private RecommendationService recommendationService;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void recommendsSixCardsThatFollowTheCompositionRules() {
		RecommendationResult result = recommend("2.0", List.of("BUSINESS"), TrackPreference.ANY, true, true);

		assertThat(result.cards()).hasSize(6);
		assertThat(result.cards()).anyMatch(card -> card.candidateKind() == CandidateKind.DESIRED_MAJOR);

		Map<String, Integer> perUniversity = new HashMap<>();
		for (RecommendedCard card : result.cards()) {
			perUniversity.merge(card.universityName(), 1, Integer::sum);
		}
		assertThat(perUniversity.values()).allMatch(count -> count <= 2);
	}

	@Test
	void neverRecommendsEligibilityRestrictedTracks() {
		RecommendationResult result = recommend("2.5", List.of("BUSINESS", "MATH"), TrackPreference.ANY, true, true);

		assertThat(result.cards()).isNotEmpty();
		assertThat(result.cards())
				.noneMatch(card -> EligibilityRestrictions.isRestricted(card.admissionTypeName()));
	}

	@Test
	void recommendsOnlyUnitsThatRecruitInTheLatestYear() {
		RecommendationResult result = recommend("3.0", List.of("BUSINESS"), TrackPreference.ANY, true, true);

		assertThat(result.cards()).isNotEmpty();
		for (RecommendedCard card : result.cards()) {
			Integer quota = jdbc.queryForObject("""
					SELECT quota FROM admission_unit_years
					WHERE unit_id = ? AND year = (SELECT MAX(year) FROM admission_unit_years)
					""", Integer.class, card.unitId());
			assertThat(quota).isNotNull().isPositive();
		}
	}

	@Test
	void desiredMajorCardsFollowThePreferredTrack() {
		RecommendationResult result = recommend("2.0", List.of("BUSINESS"), TrackPreference.SUBJECT, false, false);

		assertThat(result.cards()).isNotEmpty();
		for (RecommendedCard card : result.cards()) {
			if (card.candidateKind() == CandidateKind.DESIRED_MAJOR) {
				String trackType = jdbc.queryForObject(
						"SELECT track_type FROM admission_units WHERE id = ?", String.class, card.unitId());
				assertThat(trackType).isEqualTo("교과");
			}
		}
	}

	@Test
	void usesNoSimilarMajorCardsWhenNotAllowed() {
		RecommendationResult result = recommend("2.0", List.of("BUSINESS"), TrackPreference.ANY, false, false);

		assertThat(result.cards())
				.noneMatch(card -> card.candidateKind() == CandidateKind.SIMILAR_MAJOR);
	}

	@Test
	void unratedCardsHaveNoGradeGap() {
		RecommendationResult result = recommend("4.0", List.of("BUSINESS"), TrackPreference.ANY, true, true);

		assertThat(result.cards())
				.filteredOn(card -> card.band() == AdmissionBand.UNRATED)
				.allMatch(card -> card.gradeGap() == null);
	}

	@Test
	void handlesTheBoundaryAverages() {
		assertThat(recommend("1.0", List.of("BUSINESS"), TrackPreference.ANY, true, true).cards().size())
				.isBetween(1, 6);
		assertThat(recommend("5.0", List.of("BUSINESS"), TrackPreference.ANY, true, true).cards().size())
				.isBetween(1, 6);
	}

	private RecommendationResult recommend(
			String average,
			List<String> majors,
			TrackPreference track,
			boolean includeReach,
			boolean allowSimilar
	) {
		return recommendationService.recommend(new RecommendationQuery(
				new BigDecimal(average), majors, track, includeReach, allowSimilar));
	}

}
