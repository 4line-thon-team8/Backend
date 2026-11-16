package com.fivegrade.backend.admission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.admission.dto.UniversityTrackRow;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 대학별 전형 목록 조회 쿼리(findDistinctTracks)가 실제 DB 에서 돌아가는지 확인한다.
 * 쿼리 문법과 결과 매핑은 DB 없이 확인할 수 없어서, 적재된 시드로 확인한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UniversityTrackQueryTest {

	@Autowired
	private AdmissionUnitRepository admissionUnitRepository;

	@Test
	void 대학_전형_유형_전형이름_조합이_한_번씩만_나온다() {
		List<UniversityTrackRow> rows = admissionUnitRepository.findDistinctTracks();

		Set<UniversityTrackRow> unique = new HashSet<>(rows);
		assertThat(rows).isNotEmpty();
		assertThat(unique).hasSameSizeAs(rows);
	}

	@Test
	void 적재된_53개_대학이_모두_나온다() {
		Set<String> universities = new HashSet<>();
		for (UniversityTrackRow row : admissionUnitRepository.findDistinctTracks()) {
			universities.add(row.universityName());
		}

		assertThat(universities).hasSize(53).contains("동국대학교", "고려대학교");
	}

	@Test
	void 전형_유형은_학종과_교과뿐이다() {
		Set<String> types = new HashSet<>();
		for (UniversityTrackRow row : admissionUnitRepository.findDistinctTracks()) {
			types.add(row.trackType());
		}

		assertThat(types).containsExactlyInAnyOrder("학종", "교과");
	}

}
