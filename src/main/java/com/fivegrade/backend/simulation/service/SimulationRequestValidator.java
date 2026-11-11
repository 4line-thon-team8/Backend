package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.admission.repository.MajorRepository;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.simulation.dto.ApplicationPreference;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 요청 DTO의 형식 검증만으로 확인할 수 없는, DB가 필요한 검증을 맡는다.
 * 지금은 희망 학과 코드가 학과 사전에 있는지만 확인한다.
 */
@Component
public class SimulationRequestValidator {

	private static final String INVALID_MAJOR_CODES_MESSAGE =
			"입력값이 올바르지 않습니다: applicationPreference.desiredMajorCodes";

	private final MajorRepository majorRepository;

	public SimulationRequestValidator(MajorRepository majorRepository) {
		this.majorRepository = majorRepository;
	}

	/**
	 * 희망 학과 코드가 모두 학과 사전에 있어야 한다.
	 * 메시지에는 사용자가 보낸 코드 값을 넣지 않고 필드 이름만 쓴다.
	 */
	public void validateDesiredMajors(ApplicationPreference preference) {
		List<String> codes = preference.desiredMajorCodes();
		int distinctCount = new HashSet<>(codes).size();
		int foundCount = majorRepository.findAllById(codes).size();
		if (foundCount != distinctCount) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, INVALID_MAJOR_CODES_MESSAGE);
		}
	}

}
