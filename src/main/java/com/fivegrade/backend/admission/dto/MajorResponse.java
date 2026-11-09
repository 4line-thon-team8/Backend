package com.fivegrade.backend.admission.dto;

import com.fivegrade.backend.admission.entity.Major;

/**
 * 학과 사전의 학과 한 개. 엔티티를 그대로 내보내지 않고 화면에 필요한 값만 담는다.
 *
 * @param code 학과 코드. 분석 요청의 desiredMajorCodes 에 그대로 보내는 값
 * @param name 화면에 표시할 학과명
 */
public record MajorResponse(String code, String name) {

	public static MajorResponse from(Major major) {
		return new MajorResponse(major.getCode(), major.getName());
	}

}
