package com.fivegrade.backend.admission.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.admission.entity.Major;
import com.fivegrade.backend.admission.entity.MajorTag;
import com.fivegrade.backend.admission.entity.Tag;
import com.fivegrade.backend.admission.entity.UnitMajorMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학과 사전, 태그, 모집단위 매핑 엔티티가 V1 스키마와 맞게 읽히는지 확인한다.
 * 엔티티는 읽기 전용이라 JdbcTemplate 으로 행을 넣고 Repository 로 읽는다. 각 테스트는 롤백된다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MajorMappingRepositoryTest {

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private MajorRepository majorRepository;
	@Autowired
	private TagRepository tagRepository;
	@Autowired
	private MajorTagRepository majorTagRepository;
	@Autowired
	private UnitMajorMapRepository unitMajorMapRepository;

	private Long unitId;
	private Long codingTagId;
	private Long mathTagId;

	@BeforeEach
	void insertFixture() {
		jdbc.update("INSERT INTO majors (code, name) VALUES ('CS', '컴퓨터공학')");
		jdbc.update("INSERT INTO majors (code, name) VALUES ('SW', '소프트웨어학')");

		codingTagId = jdbc.queryForObject(
				"INSERT INTO tags (name, category) VALUES ('코딩', '탐구') RETURNING id", Long.class);
		mathTagId = jdbc.queryForObject(
				"INSERT INTO tags (name) VALUES ('수리') RETURNING id", Long.class);

		jdbc.update("INSERT INTO major_tags (major_code, tag_id, weight) VALUES ('CS', ?, 0.90)", codingTagId);
		jdbc.update("INSERT INTO major_tags (major_code, tag_id) VALUES ('CS', ?)", mathTagId);
		jdbc.update("INSERT INTO major_tags (major_code, tag_id, weight) VALUES ('SW', ?, 0.80)", codingTagId);

		Long universityId = jdbc.queryForObject(
				"INSERT INTO universities (name) VALUES ('테스트대학교') RETURNING id", Long.class);
		unitId = jdbc.queryForObject(
				"INSERT INTO admission_units (university_id, dept_name, track_type, track_name) "
						+ "VALUES (?, '컴퓨터공학부', '교과', '학교장추천') RETURNING id", Long.class, universityId);
		jdbc.update("INSERT INTO unit_major_map (unit_id, major_code) VALUES (?, 'CS')", unitId);
		jdbc.update("INSERT INTO unit_major_map (unit_id, major_code) VALUES (?, 'SW')", unitId);
	}

	@Test
	void 학과는_문자열_코드로_조회한다() {
		Major major = majorRepository.findById("CS").orElseThrow();

		assertThat(major.getCode()).isEqualTo("CS");
		assertThat(major.getName()).isEqualTo("컴퓨터공학");
	}

	@Test
	void 태그는_분류가_없으면_null로_읽힌다() {
		Tag coding = tagRepository.findByName("코딩").orElseThrow();
		Tag math = tagRepository.findByName("수리").orElseThrow();

		assertThat(coding.getCategory()).isEqualTo("탐구");
		assertThat(math.getCategory()).isNull();
	}

	@Test
	void 학과에_붙은_태그와_연관_강도를_조회한다() {
		assertThat(majorTagRepository.findByMajorCode("CS"))
				.extracting(mt -> mt.getTag().getName(), mt -> mt.getWeight() == null ? null : mt.getWeight().doubleValue())
				.containsExactlyInAnyOrder(tuple("코딩", 0.90), tuple("수리", null));
	}

	@Test
	void 같은_태그를_가진_학과들을_조회한다() {
		assertThat(majorTagRepository.findByTagId(codingTagId))
				.extracting(MajorTag::getId)
				.extracting(id -> id.majorCode())
				.containsExactlyInAnyOrder("CS", "SW");
	}

	@Test
	void 학과_코드로_연결된_모집단위를_조회한다() {
		UnitMajorMap map = unitMajorMapRepository.findByMajorCode("CS").getFirst();

		assertThat(map.getMajor().getName()).isEqualTo("컴퓨터공학");
		assertThat(map.getUnit().getId()).isEqualTo(unitId);
		assertThat(map.getUnit().getDeptName()).isEqualTo("컴퓨터공학부");
	}

	@Test
	void 모집단위에_연결된_학과들을_조회한다() {
		assertThat(unitMajorMapRepository.findByUnitId(unitId))
				.extracting(m -> m.getId().majorCode())
				.containsExactlyInAnyOrder("CS", "SW");
	}

}
