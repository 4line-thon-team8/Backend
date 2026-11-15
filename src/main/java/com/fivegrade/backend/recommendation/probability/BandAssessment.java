package com.fivegrade.backend.recommendation.probability;

import java.util.OptionalDouble;

/**
 * 한 모집단위에 대한 학생 성적의 합격 가능성 평가 결과.
 *
 * @param band        가능성 구간
 * @param probability 최종 등록자 풀 안에 들어갈 가능성(0~1). 합격 확률이 아니다. 평가 불가면 비어 있다.
 * @param margin      예측 중심 - 학생 등급. 양수면 학생이 예측 컷보다 우수하다는 뜻이다. 평가 불가면 비어 있다.
 * @param basisYears  예측에 쓴 정상 컷 연수. 평가 불가면 0이다.
 */
public record BandAssessment(
		AdmissionBand band,
		OptionalDouble probability,
		OptionalDouble margin,
		int basisYears
) {

	public static BandAssessment unrated() {
		return new BandAssessment(
				AdmissionBand.UNRATED,
				OptionalDouble.empty(),
				OptionalDouble.empty(),
				0
		);
	}
}
