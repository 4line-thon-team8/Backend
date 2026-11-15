package com.fivegrade.backend.admission.controller;

import com.fivegrade.backend.admission.dto.MajorListResponse;
import com.fivegrade.backend.admission.service.MajorService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학과 사전 조회 API. 희망 학과를 고를 때 쓰며, 로그인 없이 호출할 수 있다.
 * 여기서 내려준 code 만 분석 요청의 desiredMajorCodes 로 허용된다.
 */
@RestController
@RequestMapping("/api/majors")
public class MajorController {

	private final MajorService majorService;

	public MajorController(MajorService majorService) {
		this.majorService = majorService;
	}

	@Operation(
			summary = "학과 사전 조회",
			description = "희망 학과로 고를 수 있는 학과 목록(코드, 이름)을 이름순으로 돌려준다. "
					+ "목록이 비어 있어도 200 과 빈 배열이다. 로그인 없이 호출할 수 있다.")
	@GetMapping
	public MajorListResponse getMajors() {
		return new MajorListResponse(majorService.findAll());
	}

}
