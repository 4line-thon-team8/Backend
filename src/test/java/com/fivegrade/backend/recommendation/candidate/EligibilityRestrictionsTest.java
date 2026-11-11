package com.fivegrade.backend.recommendation.candidate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 전형 이름으로 지원 자격 제한 전형을 걸러 내는 규칙을 검증한다. DB나 Spring 없이 실행한다.
 */
class EligibilityRestrictionsTest {

	@Test
	void restrictsTracksOpenOnlyToEligibleApplicants() {
		List<String> restricted = List.of(
				"학생부종합(농어촌학생전형)",
				"학생부교과(기회균형선발Ⅰ전형)",
				"학생부종합(고른기회)",
				"학생부종합(특수교육대상자전형)",
				"학생부교과(경제배려대상자전형)",
				"학생부교과(지역인재전형)",
				"학생부종합Ⅲ(농어촌학생전형)",
				"학생부교과(사회통합전형)",
				"학생부교과(서해 5도 학생(정원외)전형)",
				"학생부종합(조기취업형계약학과전형)",
				"학생부종합(장애인등대상자전형)"
		);

		for (String trackName : restricted) {
			assertTrue(EligibilityRestrictions.isRestricted(trackName), trackName);
		}
	}

	@Test
	void keepsTracksOpenToGeneralApplicants() {
		List<String> open = List.of(
				"학생부교과(고교추천전형)",
				"학생부교과(교과우수자전형)",
				"학생부교과(지역균형전형)",
				"학생부교과(학생부교과전형)",
				"학생부종합(일반학생전형)",
				"학생부종합(네오르네상스전형)",
				"학생부종합(학생부종합Ⅰ전형)",
				"학교장추천인재",
				"Do Dream"
		);

		for (String trackName : open) {
			assertFalse(EligibilityRestrictions.isRestricted(trackName), trackName);
		}
	}

	@Test
	void treatsMissingNameAsOpen() {
		assertFalse(EligibilityRestrictions.isRestricted(null));
	}

}
