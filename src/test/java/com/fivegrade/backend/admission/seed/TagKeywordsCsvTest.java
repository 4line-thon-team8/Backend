package com.fivegrade.backend.admission.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 전공 키워드 CSV(tag-keywords.csv)의 규칙을 DB 없이 확인한다.
 * 키워드를 고치거나 늘릴 때 흔한 실수(없는 태그, 한 글자 키워드, 중복)를 마이그레이션 전에 잡는다.
 */
class TagKeywordsCsvTest {

	private static final String TAG_KEYWORDS_CSV = "seed-data/tag-keywords.csv";
	private static final String TAGS_CSV = "seed-data/tags.csv";

	@Test
	void 모든_키워드는_tags_에_있는_태그를_가리킨다() throws IOException {
		Set<String> tagNames = new HashSet<>();
		for (String[] row : read(TAGS_CSV)) {
			tagNames.add(row[0]);
		}

		List<String> unknown = new ArrayList<>();
		for (String[] row : read(TAG_KEYWORDS_CSV)) {
			if (!tagNames.contains(row[1])) {
				unknown.add(row[0] + " → " + row[1]);
			}
		}

		assertThat(unknown).isEmpty();
	}

	@Test
	void 키워드는_두_글자_이상이고_공백이_없다() throws IOException {
		List<String> invalid = new ArrayList<>();
		for (String[] row : read(TAG_KEYWORDS_CSV)) {
			String keyword = row[0];
			// 한 글자 키워드는 아무 글에나 들어 있어서 태그가 잘못 붙는다. 공백이 있으면 대조가 불안정하다.
			if (keyword.length() < 2 || keyword.length() > 50 || !keyword.equals(keyword.strip())
					|| keyword.chars().anyMatch(Character::isWhitespace)) {
				invalid.add(keyword);
			}
		}

		assertThat(invalid).isEmpty();
	}

	@Test
	void 같은_키워드_태그_쌍은_한_번만_있다() throws IOException {
		Set<String> seen = new HashSet<>();
		List<String> duplicated = new ArrayList<>();
		for (String[] row : read(TAG_KEYWORDS_CSV)) {
			if (!seen.add(row[0] + "\t" + row[1])) {
				duplicated.add(row[0] + " → " + row[1]);
			}
		}

		assertThat(duplicated).isEmpty();
	}

	@Test
	void 모든_태그에_키워드가_하나_이상_있다() throws IOException {
		Set<String> tagNames = new HashSet<>();
		for (String[] row : read(TAGS_CSV)) {
			tagNames.add(row[0]);
		}
		Set<String> covered = new HashSet<>();
		for (String[] row : read(TAG_KEYWORDS_CSV)) {
			covered.add(row[1]);
		}

		tagNames.removeAll(covered);

		assertThat(tagNames).isEmpty();
	}

	/** 헤더를 뺀 행들을 쉼표로 나눠 돌려준다. 이 CSV 들은 큰따옴표로 감싼 값이 없다. */
	private List<String[]> read(String path) throws IOException {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(in).as(path).isNotNull();
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
			List<String[]> rows = new ArrayList<>();
			String[] lines = text.split("\n");
			for (int i = 1; i < lines.length; i++) {
				if (!lines[i].isBlank()) {
					rows.add(lines[i].split(",", -1));
				}
			}
			return rows;
		}
	}

}
