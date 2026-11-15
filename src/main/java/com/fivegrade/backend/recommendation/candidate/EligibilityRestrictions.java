package com.fivegrade.backend.recommendation.candidate;

import java.util.List;

/**
 * 지원 자격이 제한된 전형을 걸러 낸다.
 *
 * 농어촌·기회균형·특수교육대상자처럼 대상자만 지원할 수 있는 전형은 일반 학생에게 추천할 수 없다.
 * 전형 DB에는 자격 제한 여부를 나타내는 컬럼이 없어서 전형 이름에 들어간 키워드로 판단한다.
 * 적재된 전형 이름을 훑어 만든 목록이라, 새 대학이 적재되어 새 이름이 생기면 여기에 추가해야 한다.
 *
 * 제외 기준은 "지원하려면 일반 수험생이 가질 수 없는 신분·가정환경·서류가 필요한가"이다.
 * 합격한 뒤에 붙는 조건(의무복무, 채용 약정 등)만 있고 누구나 지원할 수 있는 전형은 제외하지 않는다.
 * 사이버국방·국방AI·국가안보융합인재와 채용조건형·조기취업형 계약학과가 여기에 해당한다.
 * 이들은 임관 결격사유나 신체검사처럼 선발 단계의 심사 조건이 있을 뿐 신분 요건은 아니다.
 * 입학 후 조건이 있다는 안내는 별도 작업으로 다룬다.
 */
public final class EligibilityRestrictions {

	/** 이 키워드가 전형 이름에 있으면 자격 제한 전형으로 본다. */
	private static final List<String> RESTRICTED_KEYWORDS = List.of(
			// 사회적 배려·기회균형
			"농어촌", "기회균형", "고른기회", "사회통합", "사회배려", "사회다양성", "사회공헌", "고운사회", "어울림",
			"기초생활", "수급자", "차상위", "저소득", "경제배려", "한부모", "다문화", "북한이탈", "자립",
			"사회적배려", "사회기여", "기회배려", "후계농업",
			// 국가유공·보훈·직군
			"보훈", "순직", "소방", "공무원", "정부위탁",
			// 장애
			"특수교육", "장애",
			// 출신·지역·재직 요건
			"지역인재", "지역의사", "특성화", "재직자", "만학도", "평생학습", "성인학습자", "서해 5도", "서해5도", "국제형", "해외고",
			// 현역 군인·농업 종사자 등 신분이 필요한 선발
			"영농", "군위탁",
			// 종교 추천
			"불교추천", "가톨릭지도자", "재림교회", "크리스천리더", "기독교지도자", "신학특별", "웨슬리",
			// 실기·예체능
			"실기", "예체능"
	);

	/**
	 * 전형 이름에는 드러나지 않지만 모집요강상 특정 학과만 종교 서류(세례증명서 등)가 필수인 모집단위.
	 * 같은 전형의 다른 학과는 일반 학생이 지원할 수 있으므로 대학·학과·전형을 모두 지정해 제외한다.
	 */
	private static final List<RestrictedUnit> RESTRICTED_UNITS = List.of(
			// 협성대 신학과는 교과성적우수자 전형도 세례증명서가 필수다.
			new RestrictedUnit("협성대학교", "신학과", "학생부교과(교과성적우수자)"),
			// 한신대 신학 전공은 참인재 전형에서 목회자(목사·선교사·신부) 추천서가 필수다.
			// 같은 계열의 미디어융합기독교교육 등 다른 전공과 다른 전형(학생부우수자·학교장추천)은 일반 학생도 지원할 수 있다.
			new RestrictedUnit("한신대학교", "신학", "학생부교과(참인재)")
	);

	private EligibilityRestrictions() {
	}

	private record RestrictedUnit(String universityName, String deptName, String trackName) {
	}

	/** 전형 이름과 모집단위(대학·학과)로 일반 학생이 지원할 수 없다고 판단되면 true */
	public static boolean isRestricted(String universityName, String deptName, String trackName) {
		if (isRestricted(trackName)) {
			return true;
		}
		for (RestrictedUnit unit : RESTRICTED_UNITS) {
			if (unit.universityName().equals(universityName)
					&& unit.deptName().equals(deptName)
					&& unit.trackName().equals(trackName)) {
				return true;
			}
		}
		return false;
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
