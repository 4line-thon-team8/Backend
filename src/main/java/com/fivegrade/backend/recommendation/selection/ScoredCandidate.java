package com.fivegrade.backend.recommendation.selection;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;

/**
 * 합격 가능성 구간 계산을 마친 후보. 카드로 고르기 전 단계의 값이다.
 *
 * @param unitId            모집단위 ID
 * @param universityId      대학 ID
 * @param universityName    대학 이름
 * @param departmentName    학과(모집단위) 이름
 * @param admissionTypeName 전형 이름
 * @param kind              후보가 오른 경로
 * @param band              합격 가능성 구간
 * @param probability       풀 안에 들 가능성(0~1). 구간 안에서 후보를 고를 때만 쓰고 응답에는 담지 않는다. UNRATED 이면 null
 * @param gradeGap          5등급 환산 예측 70%컷 - 사용자 5등급 평균. UNRATED 이면 null
 * @param evidence          근거 지표
 * @param lowConfidence     예측 오차 폭(σ)이 커서 신뢰도가 낮은지 여부
 */
public record ScoredCandidate(
		long unitId,
		long universityId,
		String universityName,
		String departmentName,
		String admissionTypeName,
		CandidateKind kind,
		AdmissionBand band,
		Double probability,
		Double gradeGap,
		CardEvidence evidence,
		boolean lowConfidence
) {

	/** 같은 대학의 같은 학과인지 구분하는 키. 전형이 달라도 같은 학과는 한 번만 추천한다. */
	String departmentKey() {
		return universityId + "\u0000" + departmentName;
	}

}
