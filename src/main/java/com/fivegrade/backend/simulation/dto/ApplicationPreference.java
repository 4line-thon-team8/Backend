package com.fivegrade.backend.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * 지원 방향. 지원 대학은 요청에 담지 않고 서버가 적재된 전체 전형에서 추천한다.
 *
 * 희망 학과 코드가 학과 사전에 실제로 있는지는 DB를 봐야 해서
 * {@code SimulationRequestValidator} 가 따로 확인한다. 여기서는 개수와 형식만 본다.
 *
 * @param desiredMajorCodes 희망 학과 코드 1~3개 (중복 불가)
 * @param preferredTrack    선호 전형. 없으면 {@link PreferredTrack#ANY}
 * @param includeReachCard  상향 지원 카드 포함 여부. 없으면 false
 * @param allowSimilarMajors 비슷한 학과로 확대 허용 여부. 없으면 true
 */
public record ApplicationPreference(

		@Schema(description = "희망 학과 코드 1~3개(중복 불가). GET /api/majors의 code 값을 쓴다.", example = "[\"KOREAN_LIT\", \"ENGLISH_LIT\"]")
		@NotEmpty
		@Size(max = ApplicationPreference.MAX_DESIRED_MAJORS)
		@UniqueElements
		List<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,29}") String> desiredMajorCodes,

		@Schema(description = "선호 전형(SUBJECT=교과, HOLISTIC=학생부종합, ANY=상관없음). 생략하면 ANY.", defaultValue = "ANY", example = "ANY")
		PreferredTrack preferredTrack,

		@Schema(description = "상향(소신) 지원 카드를 포함할지 여부. 생략하면 false.", defaultValue = "false", example = "true")
		Boolean includeReachCard,

		@Schema(description = "비슷한 학과로 후보 확대를 허용할지 여부. 생략하면 true.", defaultValue = "true", example = "true")
		Boolean allowSimilarMajors

) {

	/** 수시 지원 한도 기준으로 희망 학과는 최대 3개다. */
	static final int MAX_DESIRED_MAJORS = 3;

	/** 값이 없는 선택 항목에 화면 기본값을 채운다. */
	public ApplicationPreference {
		if (preferredTrack == null) {
			preferredTrack = PreferredTrack.ANY;
		}
		if (includeReachCard == null) {
			includeReachCard = false;
		}
		if (allowSimilarMajors == null) {
			allowSimilarMajors = true;
		}
	}

}
