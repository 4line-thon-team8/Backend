package com.fivegrade.backend.admission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * GET /api/tag-map 응답. FE 는 entries 와 입력 텍스트를 기기 안에서 대조해 전공 키워드 태그를 만든다.
 * 한 키워드가 여러 태그에 걸릴 수 있어서 같은 keyword 가 여러 줄로 나올 수 있다.
 *
 * @param version 사전 버전. 사전 내용이 바뀌면 값이 바뀐다. FE 는 저장해 둔 값과 비교해 사전을 다시 받을지 정한다.
 * @param entries 키워드 → 태그 사전. 키워드, 태그 이름 순으로 정렬되어 있다. 비어 있어도 200 과 빈 배열
 */
public record TagMapResponse(

		@Schema(description = "사전 버전. 사전 내용이 바뀌면 값이 바뀐다. 형식은 정해져 있지 않으니 문자열 그대로 비교만 한다.",
				example = "3f9a1c0b7d42")
		String version,

		@Schema(description = "키워드 → 태그 사전. 같은 키워드가 여러 태그에 걸리면 여러 줄로 나온다.")
		List<TagEntryResponse> entries

) {
}
