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
 * V5 시드(학과 사전·태그·학과-태그)가 마이그레이션으로 적재되었는지 확인한다.
 * 앱이 뜰 때 Flyway 가 시드를 넣으므로 별도 준비 없이 바로 조회한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeedMajorDictionaryTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 학과_태그_연결_개수가_CSV와_같다() {
		assertThat(count("majors")).isEqualTo(83);
		assertThat(count("tags")).isEqualTo(30);
		assertThat(count("major_tags")).isEqualTo(228);
	}

	@Test
	void 학과_코드는_영문_대문자_슬러그다() {
		List<String> invalid = jdbc.queryForList(
				"SELECT code FROM majors WHERE code !~ '^[A-Z][A-Z0-9_]*$'", String.class);

		assertThat(invalid).isEmpty();
	}

	@Test
	void 태그_분류는_계열_8개와_분야_22개다() {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE category = '계열'", Integer.class)).isEqualTo(8);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE category = '분야'", Integer.class)).isEqualTo(22);
	}

	@Test
	void UNDECLARED_외_모든_학과에_계열_태그가_정확히_하나_붙어_있다() {
		List<String> broken = jdbc.queryForList("""
				SELECT m.code
				FROM majors m
				LEFT JOIN major_tags mt ON mt.major_code = m.code
				LEFT JOIN tags t ON t.id = mt.tag_id AND t.category = '계열'
				WHERE m.code <> 'UNDECLARED'
				GROUP BY m.code
				HAVING COUNT(t.id) <> 1
				""", String.class);

		assertThat(broken).isEmpty();
	}

	@Test
	void UNDECLARED는_무전공_모집용_코드라_태그가_없다() {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM major_tags WHERE major_code = 'UNDECLARED'", Integer.class)).isZero();
	}

	@Test
	void 컴퓨터공학은_공학계열_태그를_가진다() {
		List<String> tags = jdbc.queryForList("""
				SELECT t.name
				FROM major_tags mt JOIN tags t ON t.id = mt.tag_id
				WHERE mt.major_code = 'COMPUTER_ENG'
				""", String.class);

		assertThat(tags).contains("공학계열");
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

}
