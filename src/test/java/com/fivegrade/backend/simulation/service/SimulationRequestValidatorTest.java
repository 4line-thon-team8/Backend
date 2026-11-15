package com.fivegrade.backend.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.simulation.dto.ApplicationPreference;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 희망 학과 코드를 학과 사전(V5 시드)과 대조하는 검증 테스트.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SimulationRequestValidatorTest {

	/** 응답 메시지에 입력값이 섞이지 않는지 확인하기 위한 표식 */
	private static final String UNKNOWN_CODE = "NO_SUCH_MAJOR_999";

	@Autowired
	private SimulationRequestValidator validator;

	@Test
	void 사전에_있는_학과_코드는_통과한다() {
		ApplicationPreference preference = preference(List.of("KOREAN_LIT", "HISTORY"));

		assertThatCode(() -> validator.validateDesiredMajors(preference)).doesNotThrowAnyException();
	}

	@Test
	void 사전에_없는_학과_코드가_섞이면_INVALID_REQUEST() {
		ApplicationPreference preference = preference(List.of("HISTORY", UNKNOWN_CODE));

		assertThatThrownBy(() -> validator.validateDesiredMajors(preference))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
					assertThat(e.getMessage()).contains("desiredMajorCodes").doesNotContain(UNKNOWN_CODE);
				});
	}

	private static ApplicationPreference preference(List<String> codes) {
		return new ApplicationPreference(codes, null, null, null);
	}

}
