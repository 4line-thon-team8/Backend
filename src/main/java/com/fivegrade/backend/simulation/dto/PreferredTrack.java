package com.fivegrade.backend.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사용자가 고른 선호 전형. 자유 서술 문자열은 받지 않고 이 값만 허용한다.
 */
@Schema(description = "선호 전형. SUBJECT=교과, HOLISTIC=학생부종합, ANY=상관없음")
public enum PreferredTrack {

	/** 교과 전형 */
	SUBJECT,

	/** 학생부종합 전형 */
	HOLISTIC,

	/** 상관없음. 요청에 값이 없으면 이것으로 본다. */
	ANY

}
