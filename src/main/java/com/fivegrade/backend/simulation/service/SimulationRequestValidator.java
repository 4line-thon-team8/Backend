package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.admission.repository.MajorRepository;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.simulation.dto.ApplicationPreference;
import com.fivegrade.backend.simulation.dto.SubjectInput;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 요청 DTO의 형식 검증만으로 확인할 수 없는 검증을 맡는다.
 * 희망 학과 코드가 학과 사전에 있는지(DB), 과목 코드가 이번 배포의 허용 목록에 있는지를 확인한다.
 * DTO 형식 검증을 통과한 뒤, 평균·추천 계산을 시작하기 전에 호출한다.
 */
@Component
public class SimulationRequestValidator {

	private static final String INVALID_MAJOR_CODES_MESSAGE =
			"입력값이 올바르지 않습니다: applicationPreference.desiredMajorCodes";

	private static final String INVALID_SUBJECT_CODES_MESSAGE =
			"입력값이 올바르지 않습니다: subjects.subjectCode";

	private final MajorRepository majorRepository;
	private final SubjectCodeAllowlist subjectCodeAllowlist;

	public SimulationRequestValidator(MajorRepository majorRepository, SubjectCodeAllowlist subjectCodeAllowlist) {
		this.majorRepository = majorRepository;
		this.subjectCodeAllowlist = subjectCodeAllowlist;
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

	/**
	 * 모든 과목 코드가 허용 목록에 있어야 한다.
	 * 사전에 없는 코드든 정책 범위 밖(정보·제2외국어 등)이든, 하나라도 있으면 요청 전체를 거부한다.
	 * 해당 행만 조용히 빼고 계산하면 사용자가 입력한 과목과 다른 평균이 나오기 때문이다.
	 * 메시지에는 사용자가 보낸 코드 값을 넣지 않고 필드 이름만 쓴다.
	 */
	public void validateSubjects(List<SubjectInput> subjects) {
		for (SubjectInput subject : subjects) {
			if (!subjectCodeAllowlist.isAllowed(subject.subjectCode())) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, INVALID_SUBJECT_CODES_MESSAGE);
			}
		}
	}

}
