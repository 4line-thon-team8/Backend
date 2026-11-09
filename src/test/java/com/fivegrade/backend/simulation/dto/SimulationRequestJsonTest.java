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

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void 정상_본문은_읽힌다() {
		SimulationRequest request = jsonMapper.readValue(BASE.formatted(""), SimulationRequest.class);

		assertThat(request.subjects()).hasSize(1);
		assertThat(request.applicationPreference().preferredTrack()).isEqualTo(PreferredTrack.ANY);
	}

	@Test
	void 정의하지_않은_필드가_있으면_거부한다() {
		String json = BASE.formatted(", \"credits\": 3");

		assertThatThrownBy(() -> jsonMapper.readValue(json, SimulationRequest.class))
				.isInstanceOf(RuntimeException.class);
	}

}
