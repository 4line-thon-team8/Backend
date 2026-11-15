package com.fivegrade.backend.recommendation.dto;

import com.fivegrade.backend.recommendation.probability.AdmissionBand;

/**
 * 추천 원서 한 장. 대학·학과·전형과 합격 가능성 구간, 근거를 담는다.
 *
 * 추천 이유 문장(reason)은 여기에 없다. 이유는 리포트 단계에서 이 카드의 값으로 만든다.
 * 성적과 모델 내부 계산값(확률, 9등급 여유)은 담지 않는다.
 *
 * @param rank              지망 순서(1부터). 순서 기준은 기획 확정 전이라 추천 서비스가 정한 순서를 따른다.
 * @param unitId            모집단위 ID. 리포트 저장 때 전형 데이터 참조로 쓴다.
 * @param universityName    대학 이름
 * @param departmentName    학과(모집단위) 이름
 * @param admissionTypeName 전형 이름
 * @param band              합격 가능성 구간. 입결이 없으면 UNRATED
 * @param candidateKind     후보가 오른 경로
 * @param gradeGap          예측 70%컷을 5등급으로 환산한 값에서, 반올림 전 사용자 5등급 평균을 뺀 값.
 *                          양수면 사용자가 더 우수하다. UNRATED(근거가 되는 컷이 전혀 없음)이면 null.
 *                          예측 70%컷은 모델의 드리프트 보정 전 값이고, 9등급 → 5등급 변환의 끝값 고정 때문에
 *                          양 끝 근처에서는 근사값이다. 모델 내부의 9등급 기준 여유(margin)와는 다른 값이다.
 *                          반올림은 하지 않고 돌려주며, 표시용 반올림은 응답을 조립하는 쪽에서 한다.
 * @param evidence          근거 지표
 */
public record RecommendedCard(
		int rank,
		long unitId,
		String universityName,
		String departmentName,
		String admissionTypeName,
		AdmissionBand band,
		CandidateKind candidateKind,
		Double gradeGap,
		CardEvidence evidence
) {

	public RecommendedCard {
		if (rank < 1 || rank > RecommendationResult.MAX_CARDS) {
			throw new IllegalArgumentException("rank must be between 1 and 6");
		}
		if (isBlank(universityName) || isBlank(departmentName) || isBlank(admissionTypeName)) {
			throw new IllegalArgumentException("universityName, departmentName and admissionTypeName must not be blank");
		}
		if (band == null || candidateKind == null || evidence == null) {
			throw new IllegalArgumentException("band, candidateKind and evidence must not be null");
		}
		if (band == AdmissionBand.UNRATED && gradeGap != null) {
			throw new IllegalArgumentException("gradeGap must be null when band is UNRATED");
		}
	}

	/** 같은 대학·학과·전형인지 구분하는 키. 한 결과 안에서 겹치면 안 된다. */
	String targetKey() {
		return universityName + "\u0000" + departmentName + "\u0000" + admissionTypeName;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
