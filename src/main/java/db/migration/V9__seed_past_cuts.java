package db.migration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 2023~2025학년도 입시결과(모집 인원, 경쟁률, 50%컷, 70%컷)를 연도별 입결(admission_unit_years)에 추가한다.
 *
 * V6 이 적재한 입결은 컷이 2026학년도뿐이라, 컷 예측이 1개 연도로만 계산되어 모든 추천 카드의 신뢰도가 낮게 나왔다.
 * 이 시드로 과거 컷이 들어가면 정상 컷 최근 3개 연도(2026, 2025, 2024)로 예측할 수 있다.
 *
 * 데이터는 seed-data/unit-years-past/{key}.csv 에 대학별로 나뉘어 있다. (key 는 seed-data/universities.csv 기준)
 * CSV 는 V6 과 같이 자연키(대학 → 학과명+전형명 → 연도)로 적혀 있고, 적재할 때 DB 가 만든 모집단위 id 로 연결한다.
 * 학과명과 전형명은 V6 이 적재한 모집단위(admission_units)의 이름이다. 이름이 맞는 모집단위가 없으면 시작을 막는다.
 *
 * 이미 V6 이 적재한 행(모집단위와 연도가 같은 행)은 건드리지 않는다. 그런 행은 CSV 에 넣지 않았다.
 * (동국대학교 2023·2024학년도 학교장추천인재 94행: V6 의 평균·최저와 정리본 컷이 약 0.9등급 어긋나 산출 기준이 다른 것으로 보여 뺐다.)
 * 같은 모집단위·연도가 겹치면 UNIQUE 제약으로 마이그레이션이 실패한다.
 *
 * 등급은 어디가 공시 기준 9등급 원본이며 산출 기준은 대학별로 상이하다. 5등급 환산은 시드가 아니라 환산 엔진이 한다.
 * 이 시드에 없는 값(지원 인원, 충원 등)은 NULL 로 둔다.
 *
 * 이미 적용된 마이그레이션이므로 CSV 를 고치지 않는다. 데이터를 바꿔야 하면 새 버전(V번호)으로 추가한다.
 * (CSV 가 바뀌면 체크섬이 달라져 Flyway 가 시작을 막는다.)
 */
public class V9__seed_past_cuts extends BaseJavaMigration {

	private static final String UNIVERSITIES_CSV = "seed-data/universities.csv";
	/** 학과명과 전형명을 하나의 키로 합칠 때 쓰는 구분자. 이름에 나올 수 없는 제어 문자를 쓴다. */
	private static final char KEY_SEPARATOR = '\u001F';

	private static final String NOTE = "이전 연도 입시결과 정리본. 어디가 공시 기준(9등급 원본), 산출 기준은 대학별로 상이";
	private static final String SOURCE = "https://www.adiga.kr 대입정보포털 이전 연도 입시결과 정리본";
	private static final LocalDate COLLECTED_AT = LocalDate.of(2026, 11, 6);

	@Override
	public Integer getChecksum() {
		List<String> paths = new ArrayList<>();
		paths.add(UNIVERSITIES_CSV);
		for (Map<String, String> university : SeedCsv.read(UNIVERSITIES_CSV)) {
			paths.add(csvPath(university.get("key")));
		}
		return SeedCsv.checksum(paths.toArray(String[]::new));
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		for (Map<String, String> university : SeedCsv.read(UNIVERSITIES_CSV)) {
			Map<String, Long> unitIds = findUnitIds(connection, university.get("name"));
			insertPastYears(connection, university.get("key"), unitIds);
		}
	}

	private static String csvPath(String key) {
		return "seed-data/unit-years-past/" + key + ".csv";
	}

	/** 대학 이름으로 모집단위 id 를 "학과명+전형명" 키로 찾을 수 있게 모은다. */
	private Map<String, Long> findUnitIds(Connection connection, String universityName) throws Exception {
		Map<String, Long> unitIds = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT a.id, a.dept_name, a.track_name FROM admission_units a "
						+ "JOIN universities u ON u.id = a.university_id WHERE u.name = ?")) {
			ps.setString(1, universityName);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					unitIds.put(rs.getString("dept_name") + KEY_SEPARATOR + rs.getString("track_name"), rs.getLong("id"));
				}
			}
		}
		return unitIds;
	}

	private void insertPastYears(Connection connection, String key, Map<String, Long> unitIds) throws Exception {
		String sql = "INSERT INTO admission_unit_years (unit_id, year, quota, competition_rate, "
				+ "cut_grade_50, cut_grade_70, note, source, collected_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			for (Map<String, String> row : SeedCsv.read(csvPath(key))) {
				Long unitId = unitIds.get(row.get("dept_name") + KEY_SEPARATOR + row.get("track_name"));
				if (unitId == null) {
					throw new IllegalStateException("입결에 연결된 모집단위가 없습니다: " + key + " / " + row);
				}
				ps.setLong(1, unitId);
				ps.setInt(2, Integer.parseInt(row.get("year")));
				setInteger(ps, 3, row.get("quota"));
				setDecimal(ps, 4, row.get("competition_rate"));
				setDecimal(ps, 5, row.get("cut_grade_50"));
				setDecimal(ps, 6, row.get("cut_grade_70"));
				ps.setString(7, NOTE);
				ps.setString(8, SOURCE);
				ps.setDate(9, Date.valueOf(COLLECTED_AT));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private static void setInteger(PreparedStatement ps, int index, String value) throws Exception {
		if (value == null) {
			ps.setNull(index, Types.INTEGER);
		} else {
			ps.setInt(index, Integer.parseInt(value));
		}
	}

	private static void setDecimal(PreparedStatement ps, int index, String value) throws Exception {
		if (value == null) {
			ps.setNull(index, Types.NUMERIC);
		} else {
			ps.setBigDecimal(index, new BigDecimal(value));
		}
	}

}
