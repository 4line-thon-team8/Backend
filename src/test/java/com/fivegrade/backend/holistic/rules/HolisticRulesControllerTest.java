package com.fivegrade.backend.holistic.rules;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fivegrade.backend.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET /api/holistic-rules 응답 모양과 ETag 동작을 확인한다. DB 없이 실제 규칙 사전으로 실행한다.
 */
class HolisticRulesControllerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		HolisticRuleCatalog catalog = new HolisticRuleCatalog();
		mockMvc = MockMvcBuilders.standaloneSetup(new HolisticRulesController(catalog))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void 규칙_사전을_내려준다() throws Exception {
		mockMvc.perform(get("/api/holistic-rules"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rulesVersion").value("0.3.0"))
				.andExpect(jsonPath("$.limits.evidenceMax").value(150))
				.andExpect(jsonPath("$.limits.evidenceTextMaxChars").value(30))
				.andExpect(jsonPath("$.types.length()").value(10))
				.andExpect(jsonPath("$.indicators[0].id").exists())
				.andExpect(jsonPath("$.rules[0].id").exists())
				.andExpect(jsonPath("$.rules[0].pattern").exists())
				.andExpect(header().exists(HttpHeaders.ETAG));
	}

	@Test
	void 가중치_예시_출처는_공개하지_않는다() throws Exception {
		mockMvc.perform(get("/api/holistic-rules"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rules[0].weight").doesNotExist())
				.andExpect(jsonPath("$.rules[0].examples").doesNotExist())
				.andExpect(jsonPath("$.rules[0].source").doesNotExist());
	}

	@Test
	void 같은_ETag로_다시_요청하면_304이다() throws Exception {
		String eTag = mockMvc.perform(get("/api/holistic-rules"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);

		mockMvc.perform(get("/api/holistic-rules").header(HttpHeaders.IF_NONE_MATCH, eTag))
				.andExpect(status().isNotModified());
	}

	@Test
	void 다른_ETag면_본문을_다시_내려준다() throws Exception {
		mockMvc.perform(get("/api/holistic-rules").header(HttpHeaders.IF_NONE_MATCH, "\"stale\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rulesVersion").value("0.3.0"));
	}

}
