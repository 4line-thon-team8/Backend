package com.fivegrade.backend.admission.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대학이 운영하는 전형 한 개.
 *
 * @param type 전형 유형. 학종 또는 교과
 * @param name 전형 이름
 */
public record TrackResponse(

		@Schema(description = "전형 유형. 학종(학생부종합) 또는 교과(학생부교과)", example = "학종")
		String type,

		@Schema(description = "전형 이름", example = "학생부종합(네오르네상스전형)")
		String name

) {
}
