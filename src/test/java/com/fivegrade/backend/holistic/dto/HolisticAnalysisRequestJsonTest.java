package com.fivegrade.backend.holistic.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.simulation.dto.SimulationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 앱 설정의 JsonMapper로 holisticAnalysis 가 포함된 요청을 읽어 본다.
 * 키를 생략하면 null, 정의하지 않은 필드와 타입이 다른 값은 거부해야 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HolisticAnalysisRequestJsonTest {

	private static final String HOLISTIC = """
			"holisticAnalysis": {
			  "rulesVersion": "0.1.0",
			  "coverage": {"sectionChars": {"autonomy": 232, "club": 238, "career": 160, "subject": 584, "behavior": 128},
			               "years": [1, 2]},
			  "indicatorSignals": [{"id": "A3", "count": 6, "years": [1, 2], "quality": %s}],
			  "interestTags": [{"tag": "컴퓨터", "count": 9, "years": [1, 2]}],
			  "evidence": [{"types": ["METRIC", "METHOD"], "text": "설문 120명 자료를 수집", "year": 2, "section": "subject"}]
			  %s
			}""";

	private static final String BASE = """
			{"subjects": [{"subjectRef": "s1", "subjectCode": "KOREAN", "gradeValue": 2}],
			 "applicationPreference": {"desiredMajorCodes": ["HISTORY"]},
			 "analysisConsent": true%s}""";

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void 키를_생략하면_null이다() {
		SimulationRequest request = jsonMapper.readValue(BASE.formatted(""), SimulationRequest.class);

		assertThat(request.holisticAnalysis()).isNull();
	}

	@Test
	void 서술_분석_신호가_포함된_요청이_읽힌다() {
		String json = BASE.formatted("," + HOLISTIC.formatted("0.62", ""));

		SimulationRequest request = jsonMapper.readValue(json, SimulationRequest.class);

		HolisticAnalysisRequest holistic = request.holisticAnalysis();
		assertThat(holistic.rulesVersion()).isEqualTo("0.1.0");
		assertThat(holistic.coverage().sectionChars().total()).isEqualTo(1342);
		assertThat(holistic.indicatorSignals().get(0).quality()).isEqualTo(0.62);
		assertThat(holistic.evidence().get(0).types()).containsExactly("METRIC", "METHOD");
	}

	@Test
	void 정의하지_않은_필드는_거부한다() {
		String json = BASE.formatted("," + HOLISTIC.formatted("0.62", ", \"fullText\": \"서술 전문\""));

		assertThatThrownBy(() -> jsonMapper.readValue(json, SimulationRequest.class))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void 문자열_점수는_숫자로_바꿔_받지_않고_거부한다() {
		String json = BASE.formatted("," + HOLISTIC.formatted("\"0.62\"", ""));

		assertThatThrownBy(() -> jsonMapper.readValue(json, SimulationRequest.class))
				.isInstanceOf(RuntimeException.class);
	}

}
