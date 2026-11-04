package com.fivegrade.backend.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GlobalExceptionHandler 단위 테스트.
 * DB·전체 컨텍스트 없이, 테스트 전용 컨트롤러가 예외를 던지게 하고 응답 모양만 확인한다.
 * 모든 오류 응답은 code, message 두 필드뿐이어야 한다.
 */
class GlobalExceptionHandlerTest {

	/** 입력값이 응답에 노출되지 않는지 확인하기 위한 표식 */
	private static final String SECRET = "SECRET-VALUE-123";

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void BusinessException은_ErrorCode의_상태와_코드로_응답한다() throws Exception {
		mockMvc.perform(get("/test/business"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("리포트를 찾을 수 없습니다."))
				.andExpect(jsonPath("$.*", hasSize(2)));
	}

	@Test
	void 본문_검증_실패는_400이고_필드_이름만_알려준다() throws Exception {
		mockMvc.perform(post("/test/body")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"홍길동\",\"memo\":\"" + SECRET + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value(containsString("memo")))
				.andExpect(jsonPath("$.message").value(not(containsString(SECRET))))
				.andExpect(jsonPath("$.*", hasSize(2)));
	}

	@Test
	void JSON_형식이_깨지면_400이고_원문을_담지_않는다() throws Exception {
		mockMvc.perform(post("/test/body")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + SECRET + "\", "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value(not(containsString(SECRET))));
	}

	@Test
	void 필수_파라미터가_없으면_400이고_파라미터_이름을_알려준다() throws Exception {
		mockMvc.perform(get("/test/param"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value(containsString("count")));
	}

	@Test
	void 파라미터_타입이_맞지_않으면_400이고_값을_담지_않는다() throws Exception {
		mockMvc.perform(get("/test/param").param("count", SECRET))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value(containsString("count")))
				.andExpect(jsonPath("$.message").value(not(containsString(SECRET))));
	}

	@Test
	void 필수_헤더가_없으면_400이고_헤더_이름을_알려준다() throws Exception {
		mockMvc.perform(get("/test/header"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value(containsString("Idempotency-Key")));
	}

	@Test
	void 없는_경로는_404_NOT_FOUND다() throws Exception {
		mockMvc.perform(get("/test/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 허용되지_않는_메서드는_405다() throws Exception {
		mockMvc.perform(post("/test/business"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void 지원하지_않는_Content_Type은_415다() throws Exception {
		mockMvc.perform(post("/test/body")
						.contentType(MediaType.TEXT_PLAIN)
						.content("plain"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void 예상하지_못한_예외는_500이고_내부_정보를_담지_않는다() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getMessage()))
				.andExpect(jsonPath("$.message").value(not(containsString("db-password"))))
				.andExpect(jsonPath("$.*", hasSize(2)));
	}

	/**
	 * 테스트 전용 컨트롤러. standaloneSetup 으로만 사용한다.
	 * 테스트 클래스의 중첩 클래스라서 Spring Boot 의 TestTypeExcludeFilter 가
	 * 다른 @SpringBootTest 의 컴포넌트 스캔에서 제외해 준다.
	 */
	@RestController
	@RequestMapping("/test")
	static class ThrowingController {

		@GetMapping("/business")
		void business() {
			throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("db-password=secret");
		}

		@PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
		void body(@Valid @RequestBody SampleRequest request) {
		}

		@GetMapping("/param")
		void param(@RequestParam("count") int count) {
		}

		@GetMapping("/header")
		void header(@RequestHeader("Idempotency-Key") String key) {
		}

	}

	record SampleRequest(@NotBlank String name, @Size(max = 5) String memo) {
	}

}
