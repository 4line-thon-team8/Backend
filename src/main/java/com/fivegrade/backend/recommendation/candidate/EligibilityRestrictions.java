package com.fivegrade.backend.recommendation.candidate;

import java.util.List;

/**
 * 지원 자격이 제한된 전형을 걸러 낸다.
 *
 * 농어촌·기회균형·특수교육대상자처럼 대상자만 지원할 수 있는 전형은 일반 학생에게 추천할 수 없다.
 * 전형 DB에는 자격 제한 여부를 나타내는 컬럼이 없어서 전형 이름에 들어간 키워드로 판단한다.
 * 적재된 전형 이름을 훑어 만든 목록이라, 새 대학이 적재되어 새 이름이 생기면 여기에 추가해야 한다.
 */
public final class EligibilityRestrictions {

	/** 이 키워드가 전형 이름에 있으면 자격 제한 전형으로 본다. */
	private static final List<String> RESTRICTED_KEYWORDS = List.of(
			// 사회적 배려·기회균형
			"농어촌", "기회균형", "고른기회", "사회통합", "사회배려", "사회다양성", "사회공헌", "고운사회", "어울림",
			"기초생활", "수급자", "차상위", "저소득", "경제배려", "한부모", "다문화", "북한이탈", "자립",
			"사회적배려", "사회기여", "기회배려", "후계농업",
			// 국가유공·보훈·직군
			"보훈", "순직", "소방", "공무원", "국가안보", "국방", "정부위탁",
			// 장애
			"특수교육", "장애",
			// 출신·지역·재직 요건
			"지역인재", "지역의사", "특성화", "재직자", "만학도", "평생학습", "성인학습자", "서해 5도", "서해5도", "국제형", "해외고",
			// 계약학과·특별 선발
			"계약학과", "조기취업", "영농", "군위탁",
			// 종교 추천
			"불교추천", "가톨릭지도자", "재림교회", "크리스천리더", "기독교지도자", "신학특별",
			// 실기·예체능
			"실기", "예체능"
	);

	private EligibilityRestrictions() {
	}

	/** 전형 이름만으로 일반 학생이 지원할 수 없다고 판단되면 true */
	public static boolean isRestricted(String trackName) {
		if (trackName == null) {
			return false;
		}
		for (String keyword : RESTRICTED_KEYWORDS) {
			if (trackName.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

}
