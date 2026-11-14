package com.fivegrade.backend.report.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fivegrade.backend.auth.model.AuthenticatedUser;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.common.error.GlobalExceptionHandler;
import com.fivegrade.backend.report.dto.RecommendationDetail;
import com.fivegrade.backend.report.dto.ReportDetail;
import com.fivegrade.backend.report.dto.ReportSummary;
import com.fivegrade.backend.report.service.ReportStorageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET·DELETE /api/reports 응답 모양과 오류 코드 테스트. 저장 서비스는 가짜로 두고
 * 컨트롤러가 인증된 사용자 ID 를 그대로 넘기는지, 서비스의 404·403 을 그대로 내려주는지 본다.
 * 소유자 판단 자체는 ReportStorageServiceTest 에서 실제 DB 로 검증한다.
 */
class ReportControllerTest {

	private static final long USER_ID = 7L;

	private final ReportStorageService service = mock(ReportStorageService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void 로그인_사용자를_준비한다() {
		SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken(new AuthenticatedUser(USER_ID), null));
		mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(service))
				.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@AfterEach
	void 로그인_정보를_비운다() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 목록은_reports_배열로_감싸고_저장하지_않은_값은_담지_않는다() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.list(USER_ID)).thenReturn(List.of(new ReportSummary(id, Instant.parse("2026-11-13T00:00:00Z"), "요약")));

		mockMvc.perform(get("/api/reports"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.reports.length()").value(1))
				.andExpect(jsonPath("$.reports[0].id").value(id.toString()))
				.andExpect(jsonPath("$.reports[0].greetingSummary").value("요약"))
				.andExpect(jsonPath("$.reports[0].displayAverage").doesNotExist());
	}

	@Test
	void 목록이_비어_있으면_빈_배열이다() throws Exception {
		when(service.list(USER_ID)).thenReturn(List.of());

		mockMvc.perform(get("/api/reports"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reports").isArray())
				.andExpect(jsonPath("$.reports.length()").value(0));
	}

	@Test
	void 상세는_추천_원서를_돌려주고_전형_참조_ID는_내보내지_않는다() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.get(USER_ID, id)).thenReturn(new ReportDetail(id, Instant.parse("2026-11-13T00:00:00Z"), "요약",
				List.of(new RecommendationDetail(1, "가대학교", "컴퓨터공학과", "학생부교과(교과우수자)", "SAFE", "이유"))));

		mockMvc.perform(get("/api/reports/{id}", id))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.recommendations[0].universityName").value("가대학교"))
				.andExpect(jsonPath("$.recommendations[0].label").value("SAFE"))
				.andExpect(jsonPath("$.recommendations[0].unitId").doesNotExist());
	}

	@Test
	void 없는_리포트는_404이고_남의_리포트는_403이다() throws Exception {
		UUID missing = UUID.randomUUID();
		UUID others = UUID.randomUUID();
		when(service.get(USER_ID, missing)).thenThrow(new BusinessException(ErrorCode.REPORT_NOT_FOUND));
		when(service.get(USER_ID, others)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

		mockMvc.perform(get("/api/reports/{id}", missing))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
		mockMvc.perform(get("/api/reports/{id}", others))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 삭제하면_204이고_인증된_사용자_ID로_서비스를_호출한다() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(delete("/api/reports/{id}", id))
				.andExpect(status().isNoContent());

		verify(service).delete(USER_ID, id);
	}

	@Test
	void 남의_리포트_삭제는_403이다() throws Exception {
		UUID id = UUID.randomUUID();
		org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(service).delete(USER_ID, id);

		mockMvc.perform(delete("/api/reports/{id}", id))
				.andExpect(status().isForbidden());
	}

	@Test
	void 형식이_틀린_리포트_ID는_400이다() throws Exception {
		mockMvc.perform(get("/api/reports/{id}", "not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

}
