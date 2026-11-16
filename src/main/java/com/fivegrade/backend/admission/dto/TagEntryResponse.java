package com.fivegrade.backend.admission.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 전공 키워드 사전의 한 줄. 키워드가 입력 텍스트에 들어 있으면 그 태그가 붙는다.
 *
 * @param keyword 대조할 키워드
 * @param tag     키워드에 대응하는 태그 이름
 */
public record TagEntryResponse(

		@Schema(description = "입력 텍스트와 대조할 키워드", example = "반도체")
		String keyword,

		@Schema(description = "키워드에 대응하는 태그 이름", example = "전자·반도체")
		String tag

) {
}
