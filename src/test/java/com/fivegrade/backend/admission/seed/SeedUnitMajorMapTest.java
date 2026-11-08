package com.fivegrade.backend.admission.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_major_map", Integer.class)).isEqualTo(19_344);
	}

	@Test
	void 대부분의_모집단위가_학과에_연결되어_있다() {
		Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM admission_units", Integer.class);
		Integer mapped = jdbc.queryForObject("SELECT COUNT(DISTINCT unit_id) FROM unit_major_map", Integer.class);

		// 국방·군사·무용처럼 학과 사전에 대응하는 코드가 없는 모집단위와,
		// 이름만으로 전문직 학과(의예·약학 등)와 같다고 볼 수 없어 제외한 모집단위(제약공학·수산생명의학 등)는 연결하지 않았다.
		assertThat(mapped).isEqualTo(11_663);
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
	void 무전공_모집단위는_UNDECLARED에_연결된다() {
		List<String> majorCodes = jdbc.queryForList("""
				SELECT DISTINCT m.major_code
				FROM unit_major_map m
				JOIN admission_units a ON a.id = m.unit_id
				JOIN universities u ON u.id = a.university_id
				WHERE u.name = '한양대학교' AND a.dept_name = '한양인터칼리지학부(자연)'
				""", String.class);

		assertThat(majorCodes).containsExactly("UNDECLARED");
	}

	@Test
	void 계열_모집단위는_그_계열의_학과_전체에_연결된다() {
		List<String> majorCodes = jdbc.queryForList("""
				SELECT DISTINCT m.major_code
				FROM unit_major_map m
				JOIN admission_units a ON a.id = m.unit_id
				JOIN universities u ON u.id = a.university_id
				WHERE u.name = '전북대학교' AND a.dept_name = '경상계열'
				""", String.class);

		assertThat(majorCodes).containsExactlyInAnyOrder("BUSINESS", "ACCOUNTING", "ECONOMICS", "FINANCE",
				"INTL_TRADE", "MIS", "TOURISM", "REAL_ESTATE");
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

	@Test
	void 전문직_학과는_이름이_그_학과를_직접_가리키는_모집단위에만_연결된다() {
		// 의예·치의예·한의예·수의예·약학·간호는 면허와 직결되어, 계열·자율전공이나 비슷한 이름의 학과에
		// 연결되면 희망 학과 추천이 잘못된다. 연결은 모집단위 이름이 해당 학과를 직접 가리킬 때만 허용한다.
		Map<String, Pattern> allowed = Map.of(
				"MEDICINE", Pattern.compile("(?<![가-힣])(의예|의학과|의학부|의과)"),
				"DENTISTRY", Pattern.compile("치의(예|학)"),
				"KOREAN_MEDICINE", Pattern.compile("한의(예|학)"),
				"VETERINARY", Pattern.compile("수의(예|학)"),
				"PHARMACY", Pattern.compile("(?<!제)약학|약과학|한약학"),
				"NURSING", Pattern.compile("간호"));

		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT DISTINCT m.major_code, a.dept_name
				FROM unit_major_map m
				JOIN admission_units a ON a.id = m.unit_id
				WHERE m.major_code IN ('MEDICINE', 'DENTISTRY', 'KOREAN_MEDICINE', 'VETERINARY', 'PHARMACY', 'NURSING')
				""");

		List<String> violations = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			String majorCode = (String) row.get("major_code");
			String deptName = (String) row.get("dept_name");
			if (!allowed.get(majorCode).matcher(deptName).find()) {
				violations.add(majorCode + " <- " + deptName);
			}
		}

		assertThat(violations).isEmpty();
	}

	@Test
	void 이름이_비슷하지만_전문직이_아닌_학과는_전문직_학과에_연결되지_않는다() {
		assertThat(majorCodesOf("제주대학교", "수산생명의학과")).doesNotContain("MEDICINE");
		assertThat(majorCodesOf("충남대학교", "생명시스템과학대학자율전공"))
				.doesNotContain("MEDICINE", "PHARMACY", "VETERINARY");
		assertThat(majorCodesOf("충북대학교", "제약학과")).doesNotContain("PHARMACY");
		assertThat(majorCodesOf("국민대학교", "제약공학과")).doesNotContain("PHARMACY");
	}

	@Test
	void 의예과는_의예_학과에_그대로_연결된다() {
		assertThat(majorCodesOf("고려대학교", "의예과")).containsExactly("MEDICINE");
		assertThat(majorCodesOf("충남대학교", "수의예과")).containsExactly("VETERINARY");
	}

	@Test
	void 전남대_에너지공학계열은_에너지공학에만_연결된다() {
		assertThat(majorCodesOf("전남대학교", "에너지공학계열")).containsExactly("ENERGY_ENG");
	}

	private List<String> majorCodesOf(String universityName, String deptName) {
		return jdbc.queryForList("""
				SELECT DISTINCT m.major_code
				FROM unit_major_map m
				JOIN admission_units a ON a.id = m.unit_id
				JOIN universities u ON u.id = a.university_id
				WHERE u.name = ? AND a.dept_name = ?
				""", String.class, universityName, deptName);
	}

}
