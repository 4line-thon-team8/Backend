package com.fivegrade.backend.admission.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fivegrade.backend.admission.dto.TrackResponse;
import com.fivegrade.backend.admission.dto.UniversityListResponse;
import com.fivegrade.backend.admission.dto.UniversityResponse;
import com.fivegrade.backend.admission.service.UniversityService;
import com.fivegrade.backend.common.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET /api/universities 응답 모양 테스트. DB 없이 서비스를 고정값으로 바꿔서 확인한다.
 */
class UniversityControllerTest {

	@Test
	void 대학과_전형_목록을_universities_배열로_내려준다() throws Exception {
		MockMvc mockMvc = mockMvcWith(new UniversityListResponse(List.of(
				new UniversityResponse("건국대학교", List.of(
						new TrackResponse("교과", "학생부교과(KU지역균형전형)"),
						new TrackResponse("학종", "학생부종합(KU자기추천전형)"))),
				new UniversityResponse("동국대학교", List.of(
						new TrackResponse("학종", "Do Dream"))))));

		mockMvc.perform(get("/api/universities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.universities.length()").value(2))
				.andExpect(jsonPath("$.universities[0].name").value("건국대학교"))
				.andExpect(jsonPath("$.universities[0].tracks.length()").value(2))
				.andExpect(jsonPath("$.universities[0].tracks[0].type").value("교과"))
				.andExpect(jsonPath("$.universities[0].tracks[0].name").value("학생부교과(KU지역균형전형)"))
				.andExpect(jsonPath("$.universities[1].name").value("동국대학교"));
	}

	@Test
	void 목록이_비어_있어도_200과_빈_배열이다() throws Exception {
		MockMvc mockMvc = mockMvcWith(new UniversityListResponse(List.of()));

		mockMvc.perform(get("/api/universities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.universities").isArray())
				.andExpect(jsonPath("$.universities.length()").value(0));
	}

	private MockMvc mockMvcWith(UniversityListResponse response) {
		// 저장소 없이 정해진 응답만 돌려주는 서비스. 컨트롤러가 응답을 내보내는 부분만 확인한다.
		UniversityService service = new UniversityService(null) {

			@Override
			public UniversityListResponse findAll() {
				return response;
			}

		};
		return MockMvcBuilders.standaloneSetup(new UniversityController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

}
