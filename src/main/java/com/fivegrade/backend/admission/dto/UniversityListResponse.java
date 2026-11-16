package com.fivegrade.backend.admission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * GET /api/universities 응답. 배열을 바로 내리지 않고 한 번 감싸는 이유는
 * 나중에 필드가 생겨도 응답 형태를 깨지 않기 위해서다.
 *
 * @param universities 대학과 전형 목록. 비어 있으면 빈 배열
 */
public record UniversityListResponse(

		@Schema(description = "대학과 전형 목록. 대학 이름순. 비어 있어도 200 과 빈 배열")
		List<UniversityResponse> universities

) {
}
