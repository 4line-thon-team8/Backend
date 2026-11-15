package com.fivegrade.backend.simulation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fivegrade.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 앱 설정의 JsonMapper로 요청 본문을 읽어, 정의하지 않은 필드를 거부하는지 확인한다.
 * gradeSystem, credits 처럼 아직 받지 않기로 한 필드가 조용히 무시되면
 * 클라이언트가 값이 반영된 줄 알고 넘어가기 때문이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SimulationRequestJsonTest {

	private static final String BASE = """
			{"subjects": [{"subjectRef": "s1", "subjectCode": "KOREAN", "gradeValue": 2%s}],
			 "applicationPreference": {"desiredMajorCodes": ["HISTORY"]},
			 "analysisConsent": true}""";

	/** 등급 값 자리에 원하는 JSON 조각을 넣어 보는 본문 */
	private static final String WITH_GRADE = """
			{"subjects": [{"subjectRef": "s1", "subjectCode": "KOREAN", "gradeValue": %s}],
			 "applicationPreference": {"desiredMajorCodes": ["HISTORY"]},
			 "analysisConsent": true}""";

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void 정상_본문은_읽힌다() {
		SimulationRequest request = jsonMapper.readValue(BASE.formatted(""), SimulationRequest.class);

		assertThat(request.subjects()).hasSize(1);
		assertThat(request.applicationPreference().preferredTrack()).isEqualTo(PreferredTrack.ANY);
	}

	@Test
	void 정수_등급은_그대로_읽힌다() {
		for (int grade = 1; grade <= 5; grade++) {
			SimulationRequest request = jsonMapper.readValue(WITH_GRADE.formatted(grade), SimulationRequest.class);

			assertThat(request.subjects().get(0).gradeValue()).isEqualTo(grade);
		}
	}

	@Test
	void 소수_등급은_정수로_잘라_받지_않고_거부한다() {
		// 2.9가 2로, 5.9가 5로 조용히 바뀌면 사용자가 보낸 값과 다른 성적으로 분석된다.
		for (String grade : new String[] { "2.9", "5.9", "2.0", "1e0" }) {
			String json = WITH_GRADE.formatted(grade);

			assertThatThrownBy(() -> jsonMapper.readValue(json, SimulationRequest.class))
					.as("등급 %s", grade)
					.isInstanceOf(RuntimeException.class);
		}
	}

	@Test
	void 문자열_등급은_숫자로_바꿔_받지_않고_거부한다() {
		String json = WITH_GRADE.formatted("\"2\"");

		assertThatThrownBy(() -> jsonMapper.readValue(json, SimulationRequest.class))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void 문자열_분석_동의는_불리언으로_바꿔_받지_않고_거부한다() {
		String json = WITH_GRADE.formatted("2")
				.replace("\"analysisConsent\": true", "\"analysisConsent\": \"true\"");

		assertThatThrownBy(() -> jsonMapper.readValue(json, SimulationRequest.class))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void 정의하지_않은_필드가_있으면_거부한다() {
		String json = BASE.formatted(", \"credits\": 3");

		assertThatThrownBy(() -> jsonMapper.readValue(json, SimulationRequest.class))
				.isInstanceOf(RuntimeException.class);
	}

}
