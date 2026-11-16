package com.fivegrade.backend.admission.controller;

import com.fivegrade.backend.admission.dto.TagMapResponse;
import com.fivegrade.backend.admission.service.TagMapService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전공 키워드 사전 조회 API. FE 가 입력 텍스트와 대조해 전공 키워드 태그를 만들 때 쓰며,
 * 로그인 없이 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/tag-map")
public class TagMapController {

	private final TagMapService tagMapService;

	public TagMapController(TagMapService tagMapService) {
		this.tagMapService = tagMapService;
	}

	@Operation(
			summary = "전공 키워드 사전 조회",
			description = "키워드 → 태그 사전을 돌려준다. version 은 사전 내용이 바뀌면 달라지는 문자열이라, "
					+ "저장해 둔 값과 같으면 사전을 다시 받을 필요가 없다. 같은 키워드가 여러 태그에 걸리면 여러 줄로 나온다. "
					+ "사전이 비어 있어도 200 과 빈 배열이다. 로그인 없이 호출할 수 있다.")
	@GetMapping
	public TagMapResponse getTagMap() {
		return tagMapService.getTagMap();
	}

}
