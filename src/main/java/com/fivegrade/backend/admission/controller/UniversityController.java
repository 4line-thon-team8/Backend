package com.fivegrade.backend.admission.controller;

import com.fivegrade.backend.admission.dto.UniversityListResponse;
import com.fivegrade.backend.admission.service.UniversityService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대학·전형 목록 조회 API. 로그인 없이 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/universities")
public class UniversityController {

	private final UniversityService universityService;

	public UniversityController(UniversityService universityService) {
		this.universityService = universityService;
	}

	@Operation(
			summary = "대학·전형 목록 조회",
			description = "적재된 대학과 각 대학이 운영하는 전형을 대학 이름순으로 돌려준다. "
					+ "농어촌·기회균형처럼 대상자만 지원할 수 있는 전형은 목록에서 뺀다. "
					+ "전형은 전형 유형, 전형 이름 순이다. 목록이 비어 있어도 200 과 빈 배열이다. "
					+ "로그인 없이 호출할 수 있다.")
	@GetMapping
	public UniversityListResponse getUniversities() {
		return universityService.findAll();
	}

}
