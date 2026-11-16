package com.fivegrade.backend.admission.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fivegrade.backend.admission.dto.TagEntryResponse;
import com.fivegrade.backend.admission.dto.TagMapResponse;
import com.fivegrade.backend.admission.service.TagMapService;
import com.fivegrade.backend.common.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET /api/tag-map 응답 모양 테스트. DB 없이 서비스를 고정값으로 바꿔서 확인한다.
 */
class TagMapControllerTest {

	@Test
	void 버전과_키워드_태그_목록을_내려준다() throws Exception {
		MockMvc mockMvc = mockMvcWith(new TagMapResponse("abc123def456", List.of(
				new TagEntryResponse("반도체", "전자·반도체"),
				new TagEntryResponse("인공지능", "IT·소프트웨어"),
				new TagEntryResponse("인공지능", "데이터·AI"))));

		mockMvc.perform(get("/api/tag-map"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value("abc123def456"))
				.andExpect(jsonPath("$.entries.length()").value(3))
				.andExpect(jsonPath("$.entries[0].keyword").value("반도체"))
				.andExpect(jsonPath("$.entries[0].tag").value("전자·반도체"))
				// 같은 키워드가 여러 태그에 걸리면 줄이 따로 나온다.
				.andExpect(jsonPath("$.entries[1].keyword").value("인공지능"))
				.andExpect(jsonPath("$.entries[2].keyword").value("인공지능"));
	}

	@Test
	void 사전이_비어_있어도_200과_빈_배열이다() throws Exception {
		MockMvc mockMvc = mockMvcWith(new TagMapResponse("empty", List.of()));

		mockMvc.perform(get("/api/tag-map"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries").isArray())
				.andExpect(jsonPath("$.entries.length()").value(0));
	}

	private MockMvc mockMvcWith(TagMapResponse response) {
		// 저장소 없이 정해진 응답만 돌려주는 서비스. 컨트롤러가 응답을 내보내는 부분만 확인한다.
		TagMapService service = new TagMapService(null) {

			@Override
			public TagMapResponse getTagMap() {
				return response;
			}

		};
		return MockMvcBuilders.standaloneSetup(new TagMapController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

}
