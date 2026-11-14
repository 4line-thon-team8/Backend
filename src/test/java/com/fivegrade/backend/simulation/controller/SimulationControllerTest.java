package com.fivegrade.backend.simulation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.common.error.GlobalExceptionHandler;
import com.fivegrade.backend.conversion.calculation.FiveGradeAverageCalculator;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.recommendation.service.RecommendationService;
import com.fivegrade.backend.report.generation.TemplateReportGenerator;
import com.fivegrade.backend.simulation.dto.ApplicationPreference;
import com.fivegrade.backend.simulation.dto.SubjectInput;
import com.fivegrade.backend.simulation.service.DefaultRecommendationQueryMapper;
import com.fivegrade.backend.simulation.service.SimulationReportAssembler;
import com.fivegrade.backend.simulation.service.SimulationRequestValidator;
import com.fivegrade.backend.simulation.service.SimulationService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * POST /api/simulations 흐름과 응답 모양 테스트. DB 없이 검증기와 추천 서비스를 가짜로 바꿔서 확인한다.
 * 호출 순서(검증 -> 추천), 검증 실패 시 계산 미호출, null 필드 유지, gradeGap 반올림을 본다.
 */
class SimulationControllerTest {

	private static final String VALID_BODY = """
			{
			  "subjects": [
			    {"subjectRef": "row-001", "subjectCode": "KOR", "gradeValue": 2}
			  ],
			  "applicationPreference": {"desiredMajorCodes": ["CS"]},
			  "analysisConsent": true
			}
			""";

	private final List<String> calls = new ArrayList<>();

	@Test
	void 검증한_뒤_추천을_하고_응답을_조립한다() throws Exception {
		MockMvc mockMvc = mockMvcWith(query -> {
			calls.add("recommend");
			return twoCardResult();
		}, null);

		String body = mockMvc.perform(post("/api/simulations")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.displayAverage").value(2.0))
				.andExpect(jsonPath("$.subjects[0].subjectRef").value("row-001"))
				.andExpect(jsonPath("$.recommendations.length()").value(2))
				.andExpect(jsonPath("$.recommendations[0].rank").value(1))
				.andExpect(jsonPath("$.recommendations[0].university").value("가대학교"))
				.andExpect(jsonPath("$.recommendations[0].department").value("컴퓨터공학과"))
				.andExpect(jsonPath("$.recommendations[0].admissionType").value("학생부교과(교과우수자)"))
				.andExpect(jsonPath("$.recommendations[0].category").value("SAFE"))
				.andExpect(jsonPath("$.recommendations[0].candidateKind").value("DESIRED_MAJOR"))
				.andExpect(jsonPath("$.recommendations[0].gradeGap").value(1.23))
				.andExpect(jsonPath("$.recommendations[0].evidence.basisYears").value(3))
				.andExpect(jsonPath("$.recommendations[0].evidence.isNewOrMergedDepartment").value(false))
				.andExpect(jsonPath("$.recommendations[0].reason.generationMethod").value("template"))
				.andExpect(jsonPath("$.recommendations[1].category").value("UNRATED"))
				.andExpect(jsonPath("$.warnings[0].code").value("FEWER_THAN_SIX_CARDS"))
				.andExpect(jsonPath("$.warnings[0].message").isNotEmpty())
				.andExpect(jsonPath("$.warnings[1].code").value("UNRATED_CARD"))
				.andExpect(jsonPath("$.warnings[1].rank").value(2))
				.andExpect(jsonPath("$.report.greetingSummary.generationMethod").value("template"))
				.andExpect(jsonPath("$.report.overallAssessment.text").isNotEmpty())
				.andExpect(jsonPath("$.report.applicationStrategy.text").isNotEmpty())
				.andExpect(jsonPath("$.report.closing.text").isNotEmpty())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		// 값이 없는 필드는 키를 빼지 않고 null 로 내려간다.
		assertTrue(body.contains("\"gradeGap\":null"));
		assertTrue(body.contains("\"rank\":null"));
		assertTrue(body.contains("\"isNewOrMergedDepartment\":null"));
		// 공개 응답 필드명은 isNewOrMergedDepartment 하나뿐이다. 옛 이름이 함께 내려가면 안 된다.
		assertFalse(body.contains("\"newOrMergedDepartment\""));
		assertEquals(List.of("majors", "subjects", "recommend"), calls);
	}

	@Test
	void 추천이_0장이어도_200과_빈_목록과_NO_CANDIDATES를_돌려준다() throws Exception {
		MockMvc mockMvc = mockMvcWith(query -> RecommendationResult.empty(), null);

		mockMvc.perform(post("/api/simulations")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendations").isArray())
				.andExpect(jsonPath("$.recommendations.length()").value(0))
				.andExpect(jsonPath("$.warnings[0].code").value("NO_CANDIDATES"))
				.andExpect(jsonPath("$.report.applicationStrategy.text").isNotEmpty());
	}

	@Test
	void 형식_검증에_실패하면_검증기와_추천을_호출하지_않는다() throws Exception {
		MockMvc mockMvc = mockMvcWith(query -> {
			calls.add("recommend");
			return RecommendationResult.empty();
		}, null);
		String noConsent = VALID_BODY.replace("\"analysisConsent\": true", "\"analysisConsent\": false");

		mockMvc.perform(post("/api/simulations")
						.contentType(MediaType.APPLICATION_JSON)
						.content(noConsent))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertTrue(calls.isEmpty());
	}

	@Test
	void 학과나_과목_검증에_실패하면_400이고_추천을_호출하지_않는다() throws Exception {
		MockMvc mockMvc = mockMvcWith(query -> {
			calls.add("recommend");
			return RecommendationResult.empty();
		}, new BusinessException(ErrorCode.INVALID_REQUEST, "입력값이 올바르지 않습니다: subjects.subjectCode"));

		mockMvc.perform(post("/api/simulations")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertTrue(!calls.contains("recommend"));
	}

	@Test
	void 추천_계산이_실패하면_빈_결과로_바꾸지_않고_500이다() throws Exception {
		MockMvc mockMvc = mockMvcWith(query -> {
			throw new IllegalStateException("recommendation failure");
		}, null);

		mockMvc.perform(post("/api/simulations")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
	}

	/** 검증기와 추천 서비스만 가짜로 두고, 평균 계산·설명 생성·응답 변환은 실제 클래스를 쓴다. */
	private MockMvc mockMvcWith(RecommendationService recommendationService, BusinessException validationFailure) {
		SimulationRequestValidator validator = new SimulationRequestValidator(null, null) {

			@Override
			public void validateDesiredMajors(ApplicationPreference preference) {
				calls.add("majors");
				if (validationFailure != null) {
					throw validationFailure;
				}
			}

			@Override
			public void validateSubjects(List<SubjectInput> subjects) {
				calls.add("subjects");
			}

		};
		SimulationService service = new SimulationService(
				new FiveGradeAverageCalculator(),
				new DefaultRecommendationQueryMapper(),
				recommendationService);
		SimulationReportAssembler assembler = new SimulationReportAssembler(new TemplateReportGenerator());

		return MockMvcBuilders.standaloneSetup(new SimulationController(validator, service, assembler))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	/** 1장은 정상 구간, 2장은 평가 불가(gradeGap null). 6장 미만이라 전체 안내가 붙는다. */
	private RecommendationResult twoCardResult() {
		RecommendedCard rated = new RecommendedCard(
				1, 10L, "가대학교", "컴퓨터공학과", "학생부교과(교과우수자)",
				AdmissionBand.SAFE, CandidateKind.DESIRED_MAJOR, 1.2345,
				new CardEvidence(30, 4.5, false, 3));
		RecommendedCard unrated = new RecommendedCard(
				2, 20L, "나대학교", "소프트웨어학과", "학생부종합(일반)",
				AdmissionBand.UNRATED, CandidateKind.SIMILAR_MAJOR, null,
				new CardEvidence(null, null, null, 0));
		return new RecommendationResult(
				List.of(rated, unrated),
				List.of(
						RecommendationWarning.forResult(WarningCode.FEWER_THAN_SIX_CARDS),
						RecommendationWarning.forCard(WarningCode.UNRATED_CARD, 2)));
	}

}
