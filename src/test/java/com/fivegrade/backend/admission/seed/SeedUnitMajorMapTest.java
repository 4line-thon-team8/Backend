package com.fivegrade.backend.admission.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V7 시드(모집단위-학과 매핑)가 마이그레이션으로 적재되었는지 확인한다.
 * 앱이 뜰 때 Flyway 가 시드를 넣으므로 별도 준비 없이 바로 조회한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeedUnitMajorMapTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 적재_개수가_CSV와_같다() {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_major_map", Integer.class)).isEqualTo(12_246);
	}

	@Test
	void 대부분의_모집단위가_학과에_연결되어_있다() {
		Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM admission_units", Integer.class);
		Integer mapped = jdbc.queryForObject("SELECT COUNT(DISTINCT unit_id) FROM unit_major_map", Integer.class);

		// 자유전공·계열 모집처럼 학과를 하나로 정할 수 없는 모집단위는 일부러 연결하지 않았다.
		assertThat(mapped).isEqualTo(10_974);
		assertThat(mapped * 100 / total).isGreaterThanOrEqualTo(90);
	}

	@Test
	void 동국대_컴퓨터AI학부는_컴퓨터공학과_인공지능학에_연결된다() {
		List<String> majorCodes = jdbc.queryForList("""
				SELECT m.major_code
				FROM unit_major_map m
				JOIN admission_units a ON a.id = m.unit_id
				JOIN universities u ON u.id = a.university_id
				WHERE u.name = '동국대학교' AND a.dept_name = '컴퓨터·AI학부' AND a.track_name = 'Do Dream'
				""", String.class);

		assertThat(majorCodes).containsExactlyInAnyOrder("COMPUTER_ENG", "AI");
	}

	@Test
	void 학과_사전의_모든_학과가_한_번_이상_쓰인다() {
		List<String> unusedMajors = jdbc.queryForList("""
				SELECT code FROM majors
				WHERE NOT EXISTS (SELECT 1 FROM unit_major_map m WHERE m.major_code = majors.code)
				ORDER BY code
				""", String.class);

		assertThat(unusedMajors).isEmpty();
	}

}
