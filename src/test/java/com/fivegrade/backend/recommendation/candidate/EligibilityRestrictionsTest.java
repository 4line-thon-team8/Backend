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
				"학생부종합(장애인등대상자전형)",
				"학생부종합(사회적배려대상자)",
				"학생부교과(사회기여자(정원내)전형)",
				"학생부종합(사회기여 및 배려자 전형)",
				"학생부종합(교육기회배려자)",
				"학생부교과(지역의사선발전형)",
				"학생부종합(후계농업경영인)",
				"사이버국방전형",
				"학생부교과(성인학습자전형)",
				"정부위탁전형",
				"기독교지도자전형",
				"학생부교과(웨슬리)"
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
				"학생부종합(SW인재)",
				"학교장추천인재",
				"Do Dream"
		);

		for (String trackName : open) {
			assertFalse(EligibilityRestrictions.isRestricted(trackName), trackName);
		}
	}

	@Test
	void restrictsOnlyTheNamedUnitWhenRestrictionIsNotInTrackName() {
		String track = "학생부교과(교과성적우수자)";

		// 협성대 신학과는 세례증명서가 필수라 제외하지만, 같은 전형의 다른 학과는 남긴다.
		assertTrue(EligibilityRestrictions.isRestricted("협성대학교", "신학과", track));
		assertFalse(EligibilityRestrictions.isRestricted("협성대학교", "경영학과", track));
		assertFalse(EligibilityRestrictions.isRestricted("다른대학교", "신학과", track));
		assertFalse(EligibilityRestrictions.isRestricted("협성대학교", "신학과", "학생부교과(일반)"));
	}

	@Test
	void restrictsHanshinTheologyOnlyInChamInjaeTrack() {
		String chamInjae = "학생부교과(참인재)";

		// 한신대 신학 전공은 참인재 전형에서 목회자 추천서가 필수라 제외한다.
		assertTrue(EligibilityRestrictions.isRestricted("한신대학교", "신학", chamInjae));
		// 같은 전형의 다른 전공(미디어융합기독교교육 등)은 목회자 추천 요건이 없어 남긴다.
		assertFalse(EligibilityRestrictions.isRestricted("한신대학교", "미디어융합기독교교육", chamInjae));
		assertFalse(EligibilityRestrictions.isRestricted("한신대학교", "한국어문학", chamInjae));
		// 신학이라도 다른 전형(학생부우수자·학교장추천)에는 추천서 요건이 없어 남긴다.
		assertFalse(EligibilityRestrictions.isRestricted("한신대학교", "신학", "학생부교과(학생부우수자)"));
		assertFalse(EligibilityRestrictions.isRestricted("한신대학교", "신학", "학생부교과(학교장추천)"));
		// 다른 대학의 같은 이름 학과는 영향을 받지 않는다.
		assertFalse(EligibilityRestrictions.isRestricted("다른대학교", "신학", chamInjae));
	}

	@Test
	void treatsMissingNameAsOpen() {
		assertFalse(EligibilityRestrictions.isRestricted(null));
	}

}
