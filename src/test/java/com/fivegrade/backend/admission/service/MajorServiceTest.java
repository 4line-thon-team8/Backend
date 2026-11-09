package com.fivegrade.backend.admission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.admission.dto.MajorResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 시드된 학과 사전(V5)을 실제 DB에서 읽어 목록 규칙을 확인한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MajorServiceTest {

	@Autowired
	private MajorService majorService;

	@Test
	void 시드된_학과를_모두_내려준다() {
		List<MajorResponse> majors = majorService.findAll();

		assertThat(majors).isNotEmpty();
		assertThat(majors).extracting(MajorResponse::code).contains("KOREAN_LIT", "HISTORY", "UNDECLARED");
		assertThat(majors).extracting(MajorResponse::code).doesNotHaveDuplicates();
	}

	@Test
	void 이름순으로_정렬된다() {
		List<String> names = majorService.findAll().stream().map(MajorResponse::name).toList();

		assertThat(names).isSorted();
	}

}
