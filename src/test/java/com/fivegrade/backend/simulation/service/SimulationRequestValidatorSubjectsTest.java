package com.fivegrade.backend.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.simulation.dto.SubjectInput;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 과목 코드를 허용 목록과 대조하는 검증 테스트.
 * 학과 사전(DB)을 쓰지 않는 검증이라 Spring 컨텍스트 없이 실행한다.
 * 학과 코드 검증은 {@code SimulationRequestValidatorTest} 에서 확인한다.
 */
class SimulationRequestValidatorSubjectsTest {

	/** 응답 메시지에 입력값이 섞이지 않는지 확인하기 위한 표식 */
	private static final String UNKNOWN_CODE = "SUBJ_9999";

	/** 이 검증은 학과 저장소를 쓰지 않으므로 null 을 넘긴다. */
	private final SimulationRequestValidator validator =
			new SimulationRequestValidator(null, new SubjectCodeAllowlist());

	@Test
	void 허용_목록의_코드만_있으면_통과한다() {
		List<SubjectInput> subjects = List.of(subject("r1", "SUBJ_0001"), subject("r2", "SUBJ_0006"));

		assertThatCode(() -> validator.validateSubjects(subjects)).doesNotThrowAnyException();
	}

	@Test
	void 같은_코드를_다른_행으로_보내도_통과한다() {
		List<SubjectInput> subjects = List.of(subject("r1", "SUBJ_0001"), subject("r2", "SUBJ_0001"));

		assertThatCode(() -> validator.validateSubjects(subjects)).doesNotThrowAnyException();
	}

	@Test
	void 허용_목록에_없는_코드가_섞이면_요청_전체를_거부한다() {
		List<SubjectInput> subjects = List.of(subject("r1", "SUBJ_0001"), subject("r2", UNKNOWN_CODE));

		assertThatThrownBy(() -> validator.validateSubjects(subjects))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
					assertThat(e.getMessage()).contains("subjects.subjectCode").doesNotContain(UNKNOWN_CODE);
				});
	}

	@Test
	void 정책_범위_밖_코드도_거부한다() {
		// 0030 은 사전에 있을 수 있어도 이번 평균 정책의 허용 목록에는 없다.
		List<SubjectInput> subjects = List.of(subject("r1", "SUBJ_0030"));

		assertThatThrownBy(() -> validator.validateSubjects(subjects))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
	}

	private static SubjectInput subject(String ref, String code) {
		return new SubjectInput(ref, code, 2);
	}

}
