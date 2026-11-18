package db.migration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 학종 전형의 서류 평가 프로필(holistic_track_profiles)을 적재한다.
 *
 * 서류 평가 프로필이 없는 학종 후보는 서류 적합도를 표시만 하고 카드 밴드·확률은 바꾸지 않는다.
 * 이 시드가 들어가면 프로필이 있는 전형에서 서류 수준이 합격 가능성 보정에 반영된다.
 *
 * - document_share 는 모두 100 이다. 1단계 서류 합격 가능성을 기준으로 삼기로 했으므로, 면접이 있는 전형도 1단계 서류 비중을 쓴다.
 * - 3축 비중(학업·진로·공동체)은 대학이 비율을 공개한 전형만 채웠고, 나머지는 NULL(세 축 균등)이다.
 *   영역 구성이 3축과 달라 매핑이 불분명한 대학(서강·성균관 등)도 NULL 로 둔다.
 * - confidence 는 MEDIUM(모집요강에서 읽은 값) 또는 LOW(시행계획(안)에서 읽었거나 해석이 들어간 값)이다.
 *   verified_at 은 비워 둔다. 모집요강 원문 대조를 마친 행부터 새 마이그레이션으로 채운다.
 * - 대학·전형 이름은 V6 이 적재한 모집단위(admission_units)의 이름이다. 이름이 맞는 모집단위가 없으면 시작을 막는다.
 *
 * 이미 적용된 마이그레이션이므로 CSV 를 고치지 않는다. 값을 바꿔야 하면 새 버전(V번호)으로 추가한다.
 */
public class V12__seed_holistic_track_profiles extends BaseJavaMigration {

	private static final String CSV = "seed-data/track-profiles/holistic-track-profiles.csv";
	private static final String TRACK_KIND = "학종";
	private static final String DISCRIMINATION_TYPE = "UNKNOWN";
	private static final String SIGNAL_SET = "HOLISTIC_FULL";

	@Override
	public Integer getChecksum() {
		return SeedCsv.checksum(CSV);
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		Map<String, Long> universityIds = findUniversityIds(connection);
		Set<String> unitTracks = findUnitTracks(connection);
		String sql = "INSERT INTO holistic_track_profiles (university_id, track_name, admission_year, track_kind, "
				+ "discrimination_type, document_signal_set, document_share, academic_ratio, career_ratio, "
				+ "community_ratio, confidence, source_tier, source_note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			for (Map<String, String> row : SeedCsv.read(CSV)) {
				String university = row.get("university_name");
				Long universityId = universityIds.get(university);
				if (universityId == null) {
					throw new IllegalStateException("서류 평가 프로필의 대학을 찾을 수 없습니다: " + row);
				}
				if (!unitTracks.contains(university + "\u001F" + row.get("track_name"))) {
					throw new IllegalStateException("서류 평가 프로필에 연결된 모집단위 전형이 없습니다: " + row);
				}
				ps.setLong(1, universityId);
				ps.setString(2, row.get("track_name"));
				ps.setInt(3, Integer.parseInt(row.get("admission_year")));
				ps.setString(4, TRACK_KIND);
				ps.setString(5, DISCRIMINATION_TYPE);
				ps.setString(6, SIGNAL_SET);
				setDecimal(ps, 7, row.get("document_share"));
				setDecimal(ps, 8, row.get("academic_ratio"));
				setDecimal(ps, 9, row.get("career_ratio"));
				setDecimal(ps, 10, row.get("community_ratio"));
				ps.setString(11, row.get("confidence"));
				ps.setString(12, row.get("source_tier"));
				ps.setString(13, row.get("source_note"));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private static Map<String, Long> findUniversityIds(Connection connection) throws Exception {
		Map<String, Long> ids = new java.util.HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT id, name FROM universities");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				ids.put(rs.getString("name"), rs.getLong("id"));
			}
		}
		return ids;
	}

	/** "대학 이름 + 전형 이름" 키 집합. 시드의 전형 이름이 실제 모집단위와 맞는지 확인하는 데 쓴다. */
	private static Set<String> findUnitTracks(Connection connection) throws Exception {
		Set<String> keys = new HashSet<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT DISTINCT u.name AS university, a.track_name FROM admission_units a "
						+ "JOIN universities u ON u.id = a.university_id");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				keys.add(rs.getString("university") + "\u001F" + rs.getString("track_name"));
			}
		}
		return keys;
	}

	private static void setDecimal(PreparedStatement ps, int index, String value) throws Exception {
		if (value == null) {
			ps.setNull(index, Types.NUMERIC);
		} else {
			ps.setBigDecimal(index, new BigDecimal(value));
		}
	}

}
