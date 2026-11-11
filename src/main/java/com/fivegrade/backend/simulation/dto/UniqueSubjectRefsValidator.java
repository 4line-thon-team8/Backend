package com.fivegrade.backend.simulation.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link UniqueSubjectRefs}를 검사한다.
 *
 * 목록이 null인 경우, 행이나 subjectRef가 null인 경우는 다른 제약
 * (@NotEmpty, @NotNull, @NotBlank)이 이미 다루므로 여기서는 통과시켜 같은 오류가 겹쳐 나오지 않게 한다.
 */
public class UniqueSubjectRefsValidator implements ConstraintValidator<UniqueSubjectRefs, List<SubjectInput>> {

	@Override
	public boolean isValid(List<SubjectInput> subjects, ConstraintValidatorContext context) {
		if (subjects == null) {
			return true;
		}
		Set<String> seen = new HashSet<>();
		for (SubjectInput subject : subjects) {
			if (subject == null || subject.subjectRef() == null) {
				continue;
			}
			if (!seen.add(subject.subjectRef())) {
				return false;
			}
		}
		return true;
	}

}
