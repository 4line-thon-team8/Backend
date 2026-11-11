package com.fivegrade.backend.simulation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 과목 한 개의 성적. 사용자 성적은 5등급제 값만 받는다.
 *
 * 허용 과목 코드 목록은 환산 쪽에서 전달받은 뒤 서비스 단계에서 대조한다.
 * 여기서는 형식만 확인한다.
 *
 * @param subjectRef  요청 행과 결과를 연결하는 비식별 식별자
 * @param subjectCode 과목 코드. 대문자·숫자·밑줄만 허용한다.
 * @param gradeValue  5등급제 등급 (1~5)
 */
public record SubjectInput(

		@NotBlank
		@Size(max = SubjectInput.MAX_REF_LENGTH)
		String subjectRef,

		@NotBlank
		@Pattern(regexp = "[A-Z0-9_]{1,50}")
		String subjectCode,

		@NotNull
		@Min(1)
		@Max(5)
		Integer gradeValue

) {

	/** 식별자 최대 길이. 값 자체에 의미는 없고 과도하게 긴 입력을 막는 용도다. */
	static final int MAX_REF_LENGTH = 50;

}
