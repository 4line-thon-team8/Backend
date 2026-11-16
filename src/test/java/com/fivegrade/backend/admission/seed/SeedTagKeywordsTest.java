package com.fivegrade.backend.admission.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.admission.dto.TagEntryResponse;
import com.fivegrade.backend.admission.repository.TagKeywordRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V10 시드(전공 키워드 사전)가 마이그레이션으로 적재되었는지 확인한다.
 * 앱이 뜰 때 Flyway 가 시드를 넣으므로 별도 준비 없이 바로 조회한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeedTagKeywordsTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TagKeywordRepository tagKeywordRepository;

	@Test
	void 키워드_개수가_CSV와_같다() {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tag_keywords", Integer.class)).isEqualTo(673);
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT keyword) FROM tag_keywords", Integer.class)).isEqualTo(609);
	}

	@Test
	void 모든_태그에_키워드가_하나_이상_있다() {
		List<String> withoutKeyword = jdbc.queryForList("""
				SELECT t.name
				FROM tags t
				LEFT JOIN tag_keywords k ON k.tag_id = t.id
				WHERE k.keyword IS NULL
				""", String.class);

		assertThat(withoutKeyword).isEmpty();
	}

	@Test
	void 반도체는_전자_반도체_태그에_걸린다() {
		List<String> tags = jdbc.queryForList("""
				SELECT t.name
				FROM tag_keywords k JOIN tags t ON t.id = k.tag_id
				WHERE k.keyword = '반도체'
				""", String.class);

		assertThat(tags).contains("전자·반도체");
	}

	@Test
	void 저장소_쿼리가_키워드와_태그_이름을_그대로_꺼낸다() {
		// 쿼리 문법과 결과 매핑은 DB 없이 확인할 수 없어서, 적재된 시드로 확인한다.
		List<TagEntryResponse> entries = tagKeywordRepository.findAllEntries();

		assertThat(entries).hasSize(673);
		assertThat(entries).contains(new TagEntryResponse("반도체", "전자·반도체"));
	}

	@Test
	void 한_키워드가_여러_태그에_걸릴_수_있다() {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM tag_keywords WHERE keyword = '인공지능'", Integer.class);

		assertThat(count).isGreaterThanOrEqualTo(2);
	}

}
