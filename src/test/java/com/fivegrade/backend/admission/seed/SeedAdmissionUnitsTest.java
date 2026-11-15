package com.fivegrade.backend.admission.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V6 시드(대학·모집단위·연도별 입결·평가요소)가 마이그레이션으로 적재되었는지 확인한다.
 * 연도별 입결 행 수는 V9(과거 입결)까지 적용한 최종 상태 기준이다.
 * 앱이 뜰 때 Flyway 가 시드를 넣으므로 별도 준비 없이 바로 조회한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeedAdmissionUnitsTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 적재_개수가_CSV와_같다() {
		assertThat(count("universities")).isEqualTo(53);
		assertThat(count("admission_units")).isEqualTo(11_767);
		// V6 이 넣은 23,698행 + V9 의 과거 입결 10,441행
		assertThat(count("admission_unit_years")).isEqualTo(34_139);
		assertThat(count("eval_weights")).isEqualTo(1_489);
	}

	@Test
	void 모든_대학에_모집단위가_있고_모든_모집단위에_연도_데이터가_있다() {
		List<String> universitiesWithoutUnits = jdbc.queryForList("""
				SELECT u.name FROM universities u
				LEFT JOIN admission_units a ON a.university_id = u.id
				GROUP BY u.name HAVING COUNT(a.id) = 0
				""", String.class);
		Integer unitsWithoutYears = jdbc.queryForObject("""
				SELECT COUNT(*) FROM admission_units a
				WHERE NOT EXISTS (SELECT 1 FROM admission_unit_years y WHERE y.unit_id = a.id)
				""", Integer.class);

		assertThat(universitiesWithoutUnits).isEmpty();
		assertThat(unitsWithoutYears).isZero();
	}

	@Test
	void 전형_구분은_교과와_학종만_있다() {
		List<String> trackTypes = jdbc.queryForList(
				"SELECT DISTINCT track_type FROM admission_units ORDER BY track_type", String.class);

		assertThat(trackTypes).containsExactly("교과", "학종");
	}

	@Test
	void 건국대_입결_한_행이_CSV_값과_같다() {
		Map<String, Object> row = jdbc.queryForMap("""
				SELECT y.quota, y.competition_rate, y.cut_grade_50, y.cut_grade_70, y.fill_rank, y.collected_at
				FROM admission_unit_years y
				JOIN admission_units a ON a.id = y.unit_id
				JOIN universities u ON u.id = a.university_id
				WHERE u.name = '건국대학교' AND a.dept_name = 'KU자유전공학부'
				  AND a.track_name = 'KU자기추천' AND y.year = 2026
				""");

		assertThat(row.get("quota")).isEqualTo(183);
		assertThat(row.get("competition_rate").toString()).isEqualTo("32.75");
		assertThat(row.get("cut_grade_50").toString()).isEqualTo("2.70");
		assertThat(row.get("cut_grade_70").toString()).isEqualTo("3.27");
		assertThat(row.get("fill_rank")).isEqualTo(91);
		assertThat(row.get("collected_at").toString()).isEqualTo("2026-11-05");
	}

	@Test
	void 공시하지_않은_값은_NULL로_적재된다() {
		Integer allNullGrades = jdbc.queryForObject("""
				SELECT COUNT(*) FROM admission_unit_years
				WHERE grade_avg IS NULL AND grade_min IS NULL AND cut_grade_50 IS NULL AND cut_grade_70 IS NULL
				""", Integer.class);

		assertThat(allNullGrades).isPositive();
	}

	@Test
	void 평가요소는_동국대_입결_행에_연결되어_있다() {
		List<String> universities = jdbc.queryForList("""
				SELECT DISTINCT u.name
				FROM eval_weights e
				JOIN admission_unit_years y ON y.id = e.unit_year_id
				JOIN admission_units a ON a.id = y.unit_id
				JOIN universities u ON u.id = a.university_id
				""", String.class);

		assertThat(universities).containsExactly("동국대학교");
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

}
