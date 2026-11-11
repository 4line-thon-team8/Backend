package com.fivegrade.backend.simulation.dto;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fivegrade.backend.common.error.GlobalExceptionHandler;
import jakarta.validation.Valid;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/simulations 요청 DTO의 형식 검증 테스트.
 * 컨트롤러는 아직 없으므로, 테스트 전용 컨트롤러가 요청을 그대로 돌려주게 해서
 * 검증 결과와 오류 응답 모양만 확인한다.
 * 정의하지 않은 필드를 거부하는 설정은 실제 JsonMapper가 필요해서
 * {@code SimulationRequestJsonTest} 에서 확인한다.
 */
class SimulationRequestValidationTest {

	/** 검증 실패 응답에 입력값이 노출되지 않는지 확인하기 위한 표식 */
	private static final String SECRET = "secret-value-123";

	private static final String VALID_SUBJECT = """
			{"subjectRef": "s1", "subjectCode": "KOREAN", "gradeValue": 2}""";

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void 정상_요청은_통과하고_선택_항목에_기본값이_채워진다() throws Exception {
		send(body("[" + VALID_SUBJECT + "]", "{\"desiredMajorCodes\": [\"KOREAN_LIT\"]}", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicationPreference.preferredTrack").value("ANY"))
				.andExpect(jsonPath("$.applicationPreference.includeReachCard").value(false))
				.andExpect(jsonPath("$.applicationPreference.allowSimilarMajors").value(true));
	}

	@Test
	void 선택_항목을_보내면_그_값을_쓴다() throws Exception {
		String preference = """
				{"desiredMajorCodes": ["KOREAN_LIT", "HISTORY"], "preferredTrack": "HOLISTIC",
				 "includeReachCard": true, "allowSimilarMajors": false}""";
		send(body("[" + VALID_SUBJECT + "]", preference, "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicationPreference.preferredTrack").value("HOLISTIC"))
				.andExpect(jsonPath("$.applicationPreference.includeReachCard").value(true))
				.andExpect(jsonPath("$.applicationPreference.allowSimilarMajors").value(false));
	}

	@Test
	void 과목이_비어_있으면_400() throws Exception {
		expectInvalid(send(body("[]", validPreference(), "true")), "subjects");
	}

	@Test
	void 과목_필드가_없으면_400() throws Exception {
		String json = "{\"applicationPreference\": " + validPreference() + ", \"analysisConsent\": true}";
		expectInvalid(send(json), "subjects");
	}

	@Test
	void 등급이_범위를_벗어나면_400() throws Exception {
		for (int grade : new int[] { 0, 6 }) {
			String subject = "{\"subjectRef\": \"s1\", \"subjectCode\": \"KOREAN\", \"gradeValue\": " + grade + "}";
			expectInvalid(send(body("[" + subject + "]", validPreference(), "true")), "gradeValue");
		}
	}

	@Test
	void 등급이_없으면_400() throws Exception {
		String subject = "{\"subjectRef\": \"s1\", \"subjectCode\": \"KOREAN\"}";
		expectInvalid(send(body("[" + subject + "]", validPreference(), "true")), "gradeValue");
	}

	@Test
	void 과목_코드_형식이_틀리면_400이고_입력값은_응답에_없다() throws Exception {
		String subject = "{\"subjectRef\": \"s1\", \"subjectCode\": \"" + SECRET + "\", \"gradeValue\": 2}";
		send(body("[" + subject + "]", validPreference(), "true"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(content().string(containsString("subjectCode")))
				.andExpect(content().string(not(containsString(SECRET))));
	}

	@Test
	void 과목이_상한이면_통과하고_상한을_넘으면_400() throws Exception {
		// subjectRef가 겹치면 중복 검증에도 걸리므로, 상한만 확인하도록 행마다 다른 ref를 쓴다.
		send(body(subjectRows(SimulationRequest.MAX_SUBJECTS), validPreference(), "true"))
				.andExpect(status().isOk());
		expectInvalid(send(body(subjectRows(SimulationRequest.MAX_SUBJECTS + 1), validPreference(), "true")),
				"subjects");
	}

	@Test
	void subjectRef가_중복되면_400() throws Exception {
		String duplicated = "{\"subjectRef\": \"s1\", \"subjectCode\": \"MATH\", \"gradeValue\": 3}";

		expectInvalid(send(body("[" + VALID_SUBJECT + "," + duplicated + "]", validPreference(), "true")),
				"subjects");
	}

	@Test
	void subjectCode가_같아도_subjectRef가_다르면_통과한다() throws Exception {
		// 같은 과목의 다른 학기 성적은 같은 subjectCode로 여러 행을 보낸다.
		String otherSemester = "{\"subjectRef\": \"s2\", \"subjectCode\": \"KOREAN\", \"gradeValue\": 3}";

		send(body("[" + VALID_SUBJECT + "," + otherSemester + "]", validPreference(), "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subjects.length()").value(2));
	}

	@Test
	void 희망_학과가_비어_있으면_400() throws Exception {
		expectInvalid(send(body("[" + VALID_SUBJECT + "]", "{\"desiredMajorCodes\": []}", "true")),
				"desiredMajorCodes");
	}

	@Test
	void 희망_학과가_4개이면_400() throws Exception {
		String preference = "{\"desiredMajorCodes\": [\"A\", \"B\", \"C\", \"D\"]}";
		expectInvalid(send(body("[" + VALID_SUBJECT + "]", preference, "true")), "desiredMajorCodes");
	}

	@Test
	void 희망_학과가_중복이면_400() throws Exception {
		String preference = "{\"desiredMajorCodes\": [\"HISTORY\", \"HISTORY\"]}";
		expectInvalid(send(body("[" + VALID_SUBJECT + "]", preference, "true")), "desiredMajorCodes");
	}

	@Test
	void 희망_학과_코드가_공백이면_400() throws Exception {
		String preference = "{\"desiredMajorCodes\": [\" \"]}";
		expectInvalid(send(body("[" + VALID_SUBJECT + "]", preference, "true")), "desiredMajorCodes");
	}

	@Test
	void 지원_방향이_없으면_400() throws Exception {
		String json = "{\"subjects\": [" + VALID_SUBJECT + "], \"analysisConsent\": true}";
		expectInvalid(send(json), "applicationPreference");
	}

	@Test
	void 분석_동의가_false이거나_없으면_400() throws Exception {
		expectInvalid(send(body("[" + VALID_SUBJECT + "]", validPreference(), "false")), "analysisConsent");
		String withoutConsent = "{\"subjects\": [" + VALID_SUBJECT + "], \"applicationPreference\": "
				+ validPreference() + "}";
		expectInvalid(send(withoutConsent), "analysisConsent");
	}

	@Test
	void 허용하지_않는_선호_전형_값이면_400() throws Exception {
		String preference = "{\"desiredMajorCodes\": [\"HISTORY\"], \"preferredTrack\": \"" + SECRET + "\"}";
		send(body("[" + VALID_SUBJECT + "]", preference, "true"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(content().string(not(containsString(SECRET))));
	}

	/** subjectRef가 서로 다른 과목 행 count개를 담은 JSON 배열 */
	private static String subjectRows(int count) {
		return IntStream.rangeClosed(1, count)
				.mapToObj(i -> "{\"subjectRef\": \"s" + i + "\", \"subjectCode\": \"KOREAN\", \"gradeValue\": 2}")
				.collect(Collectors.joining(",", "[", "]"));
	}

	private static String validPreference() {
		return "{\"desiredMajorCodes\": [\"KOREAN_LIT\"]}";
	}

	private static String body(String subjects, String preference, String consent) {
		return "{\"subjects\": " + subjects + ", \"applicationPreference\": " + preference
				+ ", \"analysisConsent\": " + consent + "}";
	}

	private ResultActions send(String json) throws Exception {
		return mockMvc.perform(post("/test/simulations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	/** 400 과 INVALID_REQUEST 이고, 어느 필드가 문제인지 이름이 담겼는지 본다. */
	private void expectInvalid(ResultActions result, String field) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(content().string(containsString(field)));
	}

	/** 컨트롤러가 생기기 전까지 검증만 확인하기 위한 테스트 전용 컨트롤러 */
	@RestController
	@RequestMapping("/test/simulations")
	static class EchoController {

		@PostMapping
		SimulationRequest echo(@Valid @RequestBody SimulationRequest request) {
			return request;
		}

	}

}
