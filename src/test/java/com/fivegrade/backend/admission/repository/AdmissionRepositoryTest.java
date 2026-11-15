package com.fivegrade.backend.admission.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.admission.entity.AdmissionUnit;
import com.fivegrade.backend.admission.entity.AdmissionUnitYear;
import com.fivegrade.backend.admission.entity.EvalWeight;
import com.fivegrade.backend.admission.entity.University;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전형 엔티티가 V1 스키마와 맞게 읽히는지 확인한다.
 * 엔티티는 읽기 전용이라 JdbcTemplate 으로 행을 넣고 Repository 로 읽는다. 각 테스트는 롤백된다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AdmissionRepositoryTest {

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private UniversityRepository universityRepository;
	@Autowired
	private AdmissionUnitRepository unitRepository;
	@Autowired
	private AdmissionUnitYearRepository unitYearRepository;
	@Autowired
	private EvalWeightRepository evalWeightRepository;

	private Long universityId;
	private Long unitId;
	private Long fullYearId;
	private Long emptyYearId;

	@BeforeEach
	void insertFixture() {
		universityId = insertReturningId("INSERT INTO universities (name) VALUES ('테스트대학교') RETURNING id");
		unitId = insertReturningId(
				"INSERT INTO admission_units (university_id, dept_name, track_type, track_name) "
						+ "VALUES (" + universityId + ", '컴퓨터공학과', '교과', '학교장추천') RETURNING id");

		// 통계를 모두 채운 연도
		fullYearId = insertReturningId(
				"INSERT INTO admission_unit_years (unit_id, year, quota, applicants, competition_rate, csat_min, "
						+ "is_new_or_merged, grade_avg, grade_min, cut_grade_50, cut_grade_70, fill_rate, fill_rank, "
						+ "note, source, collected_at) VALUES (" + unitId + ", 2026, 30, 300, 10.00, '2합5', false, "
						+ "1.91, 2.40, 1.85, 2.10, 790.0, 12, '최종등록자 기준', '테스트 자료.pdf', '2026-10-30') RETURNING id");

		// 통계가 모두 비어 있는 연도 (학교가 공개하지 않은 경우)
		emptyYearId = insertReturningId(
				"INSERT INTO admission_unit_years (unit_id, year) VALUES (" + unitId + ", 2028) RETURNING id");

		jdbc.update("INSERT INTO eval_weights (unit_year_id, element, ratio) VALUES (?, '교과', 70.00)", fullYearId);
		jdbc.update("INSERT INTO eval_weights (unit_year_id, element, ratio) VALUES (?, '서류', 30.00)", fullYearId);
	}

	@Test
	void 대학을_이름으로_조회한다() {
		University university = universityRepository.findByName("테스트대학교").orElseThrow();

		assertThat(university.getId()).isEqualTo(universityId);
	}

	@Test
	void 모집단위가_대학과_연결되어_조회된다() {
		AdmissionUnit unit = unitRepository.findByUniversityId(universityId).getFirst();

		assertThat(unit.getDeptName()).isEqualTo("컴퓨터공학과");
		assertThat(unit.getTrackType()).isEqualTo("교과");
		assertThat(unit.getTrackName()).isEqualTo("학교장추천");
		assertThat(unit.getUniversity().getName()).isEqualTo("테스트대학교");
	}

	@Test
	void 통계가_채워진_연도의_모든_컬럼이_매핑된다() {
		AdmissionUnitYear year = unitYearRepository.findByUnitIdAndYear(unitId, 2026).orElseThrow();

		assertThat(year.getQuota()).isEqualTo(30);
		assertThat(year.getApplicants()).isEqualTo(300);
		assertThat(year.getCompetitionRate()).isEqualByComparingTo("10.00");
		assertThat(year.getCsatMin()).isEqualTo("2합5");
		assertThat(year.getNewOrMerged()).isFalse();
		assertThat(year.getGradeAvg()).isEqualByComparingTo("1.91");
		assertThat(year.getGradeMin()).isEqualByComparingTo("2.40");
		assertThat(year.getCutGrade50()).isEqualByComparingTo("1.85");
		assertThat(year.getCutGrade70()).isEqualByComparingTo("2.10");
		assertThat(year.getFillRate()).isEqualByComparingTo("790.0");
		assertThat(year.getFillRank()).isEqualTo(12);
		assertThat(year.getNote()).isEqualTo("최종등록자 기준");
		assertThat(year.getSource()).isEqualTo("테스트 자료.pdf");
		assertThat(year.getCollectedAt()).hasToString("2026-10-30");
		assertThat(year.getUnit().getId()).isEqualTo(unitId);
	}

	@Test
	void 통계가_비어_있는_연도는_null로_읽힌다() {
		AdmissionUnitYear year = unitYearRepository.findByUnitIdAndYear(unitId, 2028).orElseThrow();

		assertThat(year.getId()).isEqualTo(emptyYearId);
		assertThat(year.getQuota()).isNull();
		assertThat(year.getCompetitionRate()).isNull();
		assertThat(year.getGradeAvg()).isNull();
		assertThat(year.getCutGrade50()).isNull();
		assertThat(year.getFillRate()).isNull();
		assertThat(year.getFillRank()).isNull();
	}

	@Test
	void 모집단위의_연도_목록을_조회한다() {
		assertThat(unitYearRepository.findByUnitId(unitId))
				.extracting(AdmissionUnitYear::getYear)
				.containsExactlyInAnyOrder(2026, 2028);
	}

	@Test
	void 평가요소_반영비율을_복합키로_매핑해_조회한다() {
		assertThat(evalWeightRepository.findByUnitYearId(fullYearId))
				.extracting(EvalWeight::getElement, w -> w.getRatio().intValue())
				.containsExactlyInAnyOrder(
						tuple("교과", 70),
						tuple("서류", 30));
	}

	private Long insertReturningId(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

}
