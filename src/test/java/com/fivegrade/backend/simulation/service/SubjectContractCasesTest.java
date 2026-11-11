package com.fivegrade.backend.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.simulation.dto.SimulationRequest;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 과목 코드 사전 rc.3 과 함께 받은 입력 계약 사례를 실제 앱 설정으로 돌려 본다.
 *
 * 받은 사례는 문서의 reference checker 결과이지 이 서버의 실행 결과가 아니다.
 * 그래서 요청 읽기(JSON) → DTO 형식 검증 → 과목 코드 허용 목록 검증 순서로 이 서버가 실제로 같은 판정을 내리는지 확인한다.
 * 한 사례라도 판정이 다르면 어떤 사례인지 이름을 모아서 함께 알려 준다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SubjectContractCasesTest {

	private static final String CASES_PATH = "subject-dictionary/contract-cases.json";

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private Validator validator;

	@Autowired
	private SimulationRequestValidator requestValidator;

	@Test
	void 계약_사례의_허용_거부_판정이_서버와_일치한다() throws IOException {
		ContractFile contract = readContract();
		List<String> mismatches = new ArrayList<>();

		for (ContractCase contractCase : contract.cases()) {
			boolean actual = isAccepted(contractCase.request().toString());
			if (actual != contractCase.expectedAccepted()) {
				mismatches.add(contractCase.name() + " (기대: " + contractCase.expectedAccepted() + ", 실제: " + actual + ")");
			}
		}

		assertThat(contract.cases()).isNotEmpty();
		assertThat(mismatches).as("판정이 다른 계약 사례").isEmpty();
	}

	private ContractFile readContract() throws IOException {
		try (InputStream inputStream = new ClassPathResource(CASES_PATH).getInputStream()) {
			return jsonMapper.readValue(inputStream, ContractFile.class);
		}
	}

	/** 컨트롤러가 요청을 처리하는 순서와 같게 세 단계를 차례로 통과해야 허용이다. */
	private boolean isAccepted(String requestJson) {
		SimulationRequest request;
		try {
			request = jsonMapper.readValue(requestJson, SimulationRequest.class);
		} catch (RuntimeException e) {
			return false;
		}
		if (!validator.validate(request).isEmpty()) {
			return false;
		}
		try {
			requestValidator.validateSubjects(request.subjects());
		} catch (BusinessException e) {
			return false;
		}
		return true;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ContractFile(List<ContractCase> cases) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ContractCase(String name, boolean expectedAccepted, JsonNode request) {
	}

}
