package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 모집단위-학과 매핑(unit_major_map)을 보정한 내용으로 교체한다.
 *
 * V7 의 CSV 는 이미 적용된 시드라 고치지 않는다. (바뀌면 체크섬이 달라져 Flyway 가 시작을 막는다.)
 * 대신 보정을 모두 반영한 전체 매핑을 seed-data/unit-majors-v8/{key}.csv 에 두고,
 * V7 이 넣은 행을 모두 지운 뒤 이 CSV 로 다시 채운다. V7 + V8 을 적용한 최종 상태가 보정된 매핑이다.
 *
 * 보정 내용
 * - 학과명 키워드 규칙의 오분류를 바로잡았다.
 * - 무전공·계열 모집단위는 UNDECLARED 와 그 계열의 학과 전체에 연결했다.
 * - 이름만으로 전문직 학과(의예·약학 등)와 같다고 볼 수 없는 모집단위의 연결은 제거했다.
 *
 * 이 매핑을 다시 고칠 때도 이 파일과 CSV 를 수정하지 않고 새 버전(V번호)을 추가한다.
 */
public class V8__replace_unit_major_map extends BaseJavaMigration {

	private static final String UNIVERSITIES_CSV = "seed-data/universities.csv";
	/** 학과명과 전형명을 하나의 키로 합칠 때 쓰는 구분자. 이름에 나올 수 없는 제어 문자를 쓴다. */
	private static final char KEY_SEPARATOR = '\u001F';

	@Override
	public Integer getChecksum() {
		List<String> paths = new ArrayList<>();
		for (Map<String, String> university : SeedCsv.read(UNIVERSITIES_CSV)) {
			paths.add(csvPath(university.get("key")));
		}
		return SeedCsv.checksum(paths.toArray(String[]::new));
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate("DELETE FROM unit_major_map");
		}
		for (Map<String, String> university : SeedCsv.read(UNIVERSITIES_CSV)) {
			Map<String, Long> unitIds = findUnitIds(connection, university.get("name"));
			insertMappings(connection, university.get("key"), unitIds);
		}
	}

	private static String csvPath(String key) {
		return "seed-data/unit-majors-v8/" + key + ".csv";
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
			for (Map<String, String> row : SeedCsv.read(csvPath(key))) {
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
