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
 * 대학(universities), 모집단위(admission_units), 연도별 입결(admission_unit_years),
 * 평가요소 반영 비율(eval_weights) 시드.
 *
 * 데이터는 src/main/resources/seed-data/ 아래에 대학별 CSV 로 나뉘어 있다.
 *   universities.csv               대학 목록과 파일 키 (이 파일이 읽는 순서의 기준)
 *   units/{key}.csv                대학별 모집단위
 *   unit-years/{key}.csv           대학별 연도별 입결·모집인원
 *   eval-weights/{key}.csv         평가요소 반영 비율 (universities.csv 의 has_eval_weights 가 true 인 대학만)
 *
 * CSV 는 숫자 id 대신 자연키(대학 → 학과명+전형명 → 연도)로 적혀 있고, 적재할 때 DB 가 만든 id 로 연결한다.
 * 입결은 어디가 공시 기준 9등급 원본이다. 5등급 환산은 시드가 아니라 환산 엔진이 한다.
 *
 * 이미 적용된 마이그레이션이므로 CSV 를 고치지 않는다. 데이터를 바꿔야 하면 새 버전(V번호)으로 추가한다.
 * (CSV 가 바뀌면 체크섬이 달라져 Flyway 가 시작을 막는다.)
 */
public class V6__seed_admission_units extends BaseJavaMigration {

	private static final String UNIVERSITIES_CSV = "seed-data/universities.csv";
	/** 학과명과 전형명을 하나의 키로 합칠 때 쓰는 구분자. 이름에 나올 수 없는 제어 문자를 쓴다. */
	private static final char KEY_SEPARATOR = '\u001F';

	@Override
	public Integer getChecksum() {
		List<String> paths = new ArrayList<>();
		paths.add(UNIVERSITIES_CSV);
		for (Map<String, String> university : SeedCsv.read(UNIVERSITIES_CSV)) {
			String key = university.get("key");
			paths.add("seed-data/units/" + key + ".csv");
			paths.add("seed-data/unit-years/" + key + ".csv");
			if (hasEvalWeights(university)) {
				paths.add("seed-data/eval-weights/" + key + ".csv");
			}
		}
		return SeedCsv.checksum(paths.toArray(String[]::new));
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		for (Map<String, String> university : SeedCsv.read(UNIVERSITIES_CSV)) {
			String key = university.get("key");
			long universityId = insertUniversity(connection, university.get("name"));
			Map<String, Long> unitIds = insertUnits(connection, universityId, key);
			insertUnitYears(connection, key, unitIds);
			if (hasEvalWeights(university)) {
				insertEvalWeights(connection, universityId, key);
			}
		}
	}

	private static boolean hasEvalWeights(Map<String, String> university) {
		return Boolean.parseBoolean(university.get("has_eval_weights"));
	}

	private long insertUniversity(Connection connection, String name) throws Exception {
		try (PreparedStatement ps = connection.prepareStatement("INSERT INTO universities (name) VALUES (?) RETURNING id")) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}

	/** 모집단위를 넣고, "학과명+전형명" 으로 id 를 찾을 수 있는 Map 을 돌려준다. */
	private Map<String, Long> insertUnits(Connection connection, long universityId, String key) throws Exception {
		String sql = "INSERT INTO admission_units (university_id, dept_name, track_type, track_name) VALUES (?, ?, ?, ?)";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			for (Map<String, String> row : SeedCsv.read("seed-data/units/" + key + ".csv")) {
				ps.setLong(1, universityId);
				ps.setString(2, row.get("dept_name"));
				ps.setString(3, row.get("track_type"));
				ps.setString(4, row.get("track_name"));
				ps.addBatch();
			}
			ps.executeBatch();
		}

		Map<String, Long> unitIds = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT id, dept_name, track_name FROM admission_units WHERE university_id = ?")) {
			ps.setLong(1, universityId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					unitIds.put(unitKey(rs.getString("dept_name"), rs.getString("track_name")), rs.getLong("id"));
				}
			}
		}
		return unitIds;
	}

	private void insertUnitYears(Connection connection, String key, Map<String, Long> unitIds) throws Exception {
		String sql = "INSERT INTO admission_unit_years (unit_id, year, quota, applicants, competition_rate, csat_min, "
				+ "is_new_or_merged, grade_avg, grade_min, cut_grade_50, cut_grade_70, fill_rate, fill_rank, note, "
				+ "source, collected_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			for (Map<String, String> row : SeedCsv.read("seed-data/unit-years/" + key + ".csv")) {
				ps.setLong(1, requireUnitId(unitIds, row));
				ps.setInt(2, Integer.parseInt(row.get("year")));
				setInteger(ps, 3, row.get("quota"));
				setInteger(ps, 4, row.get("applicants"));
				setDecimal(ps, 5, row.get("competition_rate"));
				ps.setString(6, row.get("csat_min"));
				if (row.get("is_new_or_merged") == null) {
					ps.setNull(7, Types.BOOLEAN);
				} else {
					ps.setBoolean(7, Boolean.parseBoolean(row.get("is_new_or_merged")));
				}
				setDecimal(ps, 8, row.get("grade_avg"));
				setDecimal(ps, 9, row.get("grade_min"));
				setDecimal(ps, 10, row.get("cut_grade_50"));
				setDecimal(ps, 11, row.get("cut_grade_70"));
				setDecimal(ps, 12, row.get("fill_rate"));
				setInteger(ps, 13, row.get("fill_rank"));
				ps.setString(14, row.get("note"));
				ps.setString(15, row.get("source"));
				String collectedAt = row.get("collected_at");
				ps.setDate(16, collectedAt == null ? null : Date.valueOf(LocalDate.parse(collectedAt)));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void insertEvalWeights(Connection connection, long universityId, String key) throws Exception {
		// 연도별 입결 행의 id 를 "학과명+전형명+연도" 로 찾을 수 있게 모아 둔다.
		Map<String, Long> unitYearIds = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT y.id, u.dept_name, u.track_name, y.year FROM admission_unit_years y "
						+ "JOIN admission_units u ON u.id = y.unit_id WHERE u.university_id = ?")) {
			ps.setLong(1, universityId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					unitYearIds.put(unitKey(rs.getString("dept_name"), rs.getString("track_name")) + KEY_SEPARATOR
							+ rs.getInt("year"), rs.getLong("id"));
				}
			}
		}

		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO eval_weights (unit_year_id, element, ratio) VALUES (?, ?, ?)")) {
			for (Map<String, String> row : SeedCsv.read("seed-data/eval-weights/" + key + ".csv")) {
				String lookup = unitKey(row.get("dept_name"), row.get("track_name")) + KEY_SEPARATOR + row.get("year");
				Long unitYearId = unitYearIds.get(lookup);
				if (unitYearId == null) {
					throw new IllegalStateException("평가요소에 연결된 입결 행이 없습니다: " + key + " / " + row);
				}
				ps.setLong(1, unitYearId);
				ps.setString(2, row.get("element"));
				ps.setBigDecimal(3, new BigDecimal(row.get("ratio")));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private static long requireUnitId(Map<String, Long> unitIds, Map<String, String> row) {
		Long unitId = unitIds.get(unitKey(row.get("dept_name"), row.get("track_name")));
		if (unitId == null) {
			throw new IllegalStateException("입결에 연결된 모집단위가 없습니다: " + row.get("dept_name") + " / " + row.get("track_name"));
		}
		return unitId;
	}

	private static String unitKey(String deptName, String trackName) {
		return deptName + KEY_SEPARATOR + trackName;
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
