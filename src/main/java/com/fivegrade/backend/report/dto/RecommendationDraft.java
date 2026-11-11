package com.fivegrade.backend.report.dto;

/**
 * 저장할 추천 원서 한 장. 분석 결과에서 저장에 필요한 값만 옮겨 담는다.
 * 성적, 환산 결과, 가능성 수치 같은 값은 받지 않는다.
 *
 * @param rank           지망 순서 (1~6)
 * @param universityName 대학 이름 (저장 시점의 값)
 * @param deptName       학과 이름
 * @param trackName      전형 이름
 * @param label          구간 라벨 (SAFE/MATCH/REACH 등). 없으면 null
 * @param reason         추천 이유 문장
 * @param unitId         전형 데이터 참조 ID. 없으면 null
 */
public record RecommendationDraft(
		int rank,
		String universityName,
		String deptName,
		String trackName,
		String label,
		String reason,
		Long unitId) {

}
