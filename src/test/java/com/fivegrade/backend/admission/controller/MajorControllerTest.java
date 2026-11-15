package com.fivegrade.backend.admission.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fivegrade.backend.admission.dto.MajorResponse;
import com.fivegrade.backend.admission.service.MajorService;
import com.fivegrade.backend.common.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET /api/majors 응답 모양 테스트. DB 없이 서비스를 고정값으로 바꿔서 확인한다.
 */
class MajorControllerTest {

	@Test
	void 학과_목록을_majors_배열로_내려준다() throws Exception {
		MockMvc mockMvc = mockMvcWith(List.of(
				new MajorResponse("HISTORY", "사학과"),
				new MajorResponse("KOREAN_LIT", "국어국문학과")));

		mockMvc.perform(get("/api/majors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.majors.length()").value(2))
				.andExpect(jsonPath("$.majors[0].code").value("HISTORY"))
				.andExpect(jsonPath("$.majors[0].name").value("사학과"))
				.andExpect(jsonPath("$.majors[1].code").value("KOREAN_LIT"));
	}

	@Test
	void 목록이_비어_있어도_200과_빈_배열이다() throws Exception {
		MockMvc mockMvc = mockMvcWith(List.of());

		mockMvc.perform(get("/api/majors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.majors").isArray())
				.andExpect(jsonPath("$.majors.length()").value(0));
	}

	private MockMvc mockMvcWith(List<MajorResponse> majors) {
		// 저장소 없이 목록만 돌려주는 서비스. 컨트롤러가 응답을 만드는 부분만 확인한다.
		MajorService service = new MajorService(null) {

			@Override
			public List<MajorResponse> findAll() {
				return majors;
			}

		};
		return MockMvcBuilders.standaloneSetup(new MajorController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

}
