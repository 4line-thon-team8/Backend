package com.fivegrade.backend.admission.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.TestcontainersConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V9 시드(2023~2025학년도 과거 입결)가 마이그레이션으로 적재되었는지 확인한다.
 * 앱이 뜰 때 Flyway 가 시드를 넣으므로 별도 준비 없이 바로 조회한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeedPastCutsTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 연도별로_컷이_있는_행_수가_CSV와_같다() {
		Map<Integer, Integer> byYear = cutRowsByYear();

		assertThat(byYear).containsEntry(2023, 2_513);
		assertThat(byYear).containsEntry(2024, 3_675);
		assertThat(byYear).containsEntry(2025, 4_253);
		assertThat(byYear).containsEntry(2026, 6_068);
	}

	@Test
	void 컷_예측에_쓸_최근_연도는_2026_2025_2024_순서다() {
		List<Integer> years = jdbc.queryForList("""
				SELECT DISTINCT year FROM admission_unit_years
				WHERE cut_grade_50 IS NOT NULL AND cut_grade_70 IS NOT NULL
				ORDER BY year DESC
				""", Integer.class);

		assertThat(years).containsExactly(2026, 2025, 2024, 2023);
	}

	@Test
	void 과거_입결_한_행이_CSV_값과_같다() {
		Map<String, Object> row = jdbc.queryForMap("""
				SELECT y.quota, y.competition_rate, y.cut_grade_50, y.cut_grade_70, y.collected_at
				FROM admission_unit_years y
				JOIN admission_units a ON a.id = y.unit_id
				JOIN universities u ON u.id = a.university_id
				WHERE u.name = '고려대학교' AND a.dept_name = '경영대학'
				  AND a.track_name = '학교추천' AND y.year = 2025
				""");

		assertThat(row.get("quota")).isEqualTo(52);
		assertThat(row.get("competition_rate").toString()).isEqualTo("6.13");
		assertThat(row.get("cut_grade_50").toString()).isEqualTo("1.29");
		assertThat(row.get("cut_grade_70").toString()).isEqualTo("1.36");
		assertThat(row.get("collected_at").toString()).isEqualTo("2026-11-06");
	}

	@Test
	void V6이_적재한_과거_행은_바뀌지_않았다() {
		// 동국대 2024학년도 학교장추천인재는 V6 값(평균·최저)만 있고 V9 이 컷을 넣지 않는다.
		Map<String, Object> row = jdbc.queryForMap("""
				SELECT y.grade_avg, y.grade_min, y.cut_grade_50, y.cut_grade_70
				FROM admission_unit_years y
				JOIN admission_units a ON a.id = y.unit_id
				JOIN universities u ON u.id = a.university_id
				WHERE u.name = '동국대학교' AND a.dept_name = '불교학부'
				  AND a.track_name = '학교장추천인재' AND y.year = 2024
				""");

		assertThat(row.get("grade_avg").toString()).isEqualTo("2.58");
		assertThat(row.get("grade_min").toString()).isEqualTo("2.87");
		assertThat(row.get("cut_grade_50")).isNull();
		assertThat(row.get("cut_grade_70")).isNull();
	}

	@Test
	void 과거_컷은_9등급_범위이고_공시하지_않은_값은_NULL이다() {
		Integer outOfRange = jdbc.queryForObject("""
				SELECT COUNT(*) FROM admission_unit_years
				WHERE year BETWEEN 2023 AND 2025
				  AND ((cut_grade_50 NOT BETWEEN 1 AND 9) OR (cut_grade_70 NOT BETWEEN 1 AND 9))
				""", Integer.class);
		Integer pastRowsWithoutApplicants = jdbc.queryForObject("""
				SELECT COUNT(*) FROM admission_unit_years
				WHERE year BETWEEN 2023 AND 2025 AND cut_grade_50 IS NOT NULL AND applicants IS NULL
				""", Integer.class);

		assertThat(outOfRange).isZero();
		assertThat(pastRowsWithoutApplicants).isPositive();
	}

	private Map<Integer, Integer> cutRowsByYear() {
		Map<Integer, Integer> counts = new HashMap<>();
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT year, COUNT(*) AS n FROM admission_unit_years
				WHERE cut_grade_50 IS NOT NULL AND cut_grade_70 IS NOT NULL
				GROUP BY year
				""");
		for (Map<String, Object> row : rows) {
			counts.put(((Number) row.get("year")).intValue(), ((Number) row.get("n")).intValue());
		}
		return counts;
	}

}
