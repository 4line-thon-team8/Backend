package com.fivegrade.backend.simulation.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 과목 목록 안에서 subjectRef가 겹치지 않아야 한다.
 *
 * subjectRef는 요청 행과 결과를 연결하는 식별자라서, 겹치면 어느 행의 결과인지 알 수 없다.
 * 같은 과목의 다른 학기 성적은 같은 subjectCode로 여러 행을 보낼 수 있어야 하므로
 * subjectCode는 겹쳐도 되고 subjectRef만 검사한다.
 */
@Documented
@Constraint(validatedBy = UniqueSubjectRefsValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
		ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueSubjectRefs {

	/** 오류 응답에는 필드 이름만 나가므로 이 문구는 응답에 쓰이지 않는다. */
	String message() default "subjectRef는 요청 안에서 중복될 수 없습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
