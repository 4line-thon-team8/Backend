package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 모집단위-학과 매핑(unit_major_map) 시드.
 *
 * 대학마다 이름이 다른 모집단위(예: 컴퓨터·AI학부)를 학과 사전의 대표 학과(예: COMPUTER_ENG)에 연결한다.
 * 추천은 이 매핑으로 "희망 학과 → 지원 가능한 모집단위"를 찾는다.
 *
 * 데이터는 seed-data/unit-majors/{key}.csv (대학 키는 universities.csv 와 같다)에 있고,
 * 모집단위는 학과명+전형명으로, 학과는 majors.code 로 적혀 있다.
 * 매핑은 학과명 키워드 규칙으로 자동 생성한 뒤 표본 검수한 것이라 오분류가 있을 수 있다.
 * 잘못된 매핑을 고칠 때는 이 파일을 수정하지 않고 새 버전(V번호)으로 추가한다.
 * (CSV 가 바뀌면 체크섬이 달라져 Flyway 가 시작을 막는다.)
 */
public class V7__seed_unit_major_map extends BaseJavaMigration {

	private static final String UNIVERSITIES_CSV = "seed-data/universities.csv";
	/** 학과명과 전형명을 하나의 키로 합칠 때 쓰는 구분자. 이름에 나올 수 없는 제어 문자를 쓴다. */
	private static final char KEY_SEPARATOR = '\u001F';

	@Override
	public Integer getChecksum() {
		List<String> paths = new ArrayList<>();
		for (Map<String, String> university : SeedCsv.read(UNIVERSITIES_CSV)) {
			paths.add("seed-data/unit-majors/" + university.get("key") + ".csv");
		}
		return SeedCsv.checksum(paths.toArray(String[]::new));
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		for (Map<String, String> university : SeedCsv.read(UNIVERSITIES_CSV)) {
			Map<String, Long> unitIds = findUnitIds(connection, university.get("name"));
			insertMappings(connection, university.get("key"), unitIds);
		}
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

	private void insertMappings(Connection connection, String key, Map<String, Long> unitIds) throws Exception {
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO unit_major_map (unit_id, major_code) VALUES (?, ?)")) {
			for (Map<String, String> row : SeedCsv.read("seed-data/unit-majors/" + key + ".csv")) {
				Long unitId = unitIds.get(row.get("dept_name") + KEY_SEPARATOR + row.get("track_name"));
				if (unitId == null) {
					throw new IllegalStateException("매핑에 연결된 모집단위가 없습니다: " + key + " / " + row);
				}
				ps.setLong(1, unitId);
				ps.setString(2, row.get("major_code"));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

}
