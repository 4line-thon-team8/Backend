package com.fivegrade.backend.admission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 대학 한 곳과 그 대학이 운영하는 전형 목록.
 *
 * @param name   대학 이름
 * @param tracks 전형 목록. 전형 유형, 전형 이름 순
 */
public record UniversityResponse(

		@Schema(description = "대학 이름", example = "동국대학교")
		String name,

		@Schema(description = "지원할 수 있는 전형 목록. 전형 유형, 전형 이름 순")
		List<TrackResponse> tracks

) {
}
