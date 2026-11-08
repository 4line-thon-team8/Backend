package db.migration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 학과 사전(majors), 태그(tags), 학과-태그(major_tags) 시드.
 *
 * 데이터는 src/main/resources/seed-data/ 의 CSV 에 있고, 이 클래스는 읽어서 넣기만 한다.
 * CSV 는 숫자 id 대신 자연키(학과 코드, 태그 이름)로 적혀 있어서, 적재할 때 태그 id 를 이름으로 찾아 연결한다.
 * 엔티티가 읽기 전용(@Immutable)이라 JPA 가 아니라 JDBC 로 넣는다.
 *
 * 이미 적용된 마이그레이션이므로 CSV 를 고치지 않는다. 데이터를 바꿔야 하면 새 버전(V번호)으로 추가한다.
 * (CSV 가 바뀌면 체크섬이 달라져 Flyway 가 시작을 막는다.)
 */
public class V5__seed_major_dictionary extends BaseJavaMigration {

	private static final String MAJORS_CSV = "seed-data/majors.csv";
	private static final String TAGS_CSV = "seed-data/tags.csv";
	private static final String MAJOR_TAGS_CSV = "seed-data/major_tags.csv";

	@Override
	public Integer getChecksum() {
		return SeedCsv.checksum(MAJORS_CSV, TAGS_CSV, MAJOR_TAGS_CSV);
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		insertMajors(connection);
		insertTags(connection);
		insertMajorTags(connection);
	}

	private void insertMajors(Connection connection) throws Exception {
		try (PreparedStatement ps = connection.prepareStatement("INSERT INTO majors (code, name) VALUES (?, ?)")) {
			for (Map<String, String> row : SeedCsv.read(MAJORS_CSV)) {
				ps.setString(1, row.get("code"));
				ps.setString(2, row.get("name"));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void insertTags(Connection connection) throws Exception {
		try (PreparedStatement ps = connection.prepareStatement("INSERT INTO tags (name, category) VALUES (?, ?)")) {
			for (Map<String, String> row : SeedCsv.read(TAGS_CSV)) {
				ps.setString(1, row.get("name"));
				ps.setString(2, row.get("category"));
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void insertMajorTags(Connection connection) throws Exception {
		String sql = "INSERT INTO major_tags (major_code, tag_id, weight) "
				+ "SELECT ?, id, ? FROM tags WHERE name = ?";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			for (Map<String, String> row : SeedCsv.read(MAJOR_TAGS_CSV)) {
				ps.setString(1, row.get("major_code"));
				String weight = row.get("weight");
				ps.setBigDecimal(2, weight == null ? null : new BigDecimal(weight));
				ps.setString(3, row.get("tag_name"));
				// 태그 이름이 없으면 SELECT 결과가 0행이라 조용히 빠지므로, 건별로 실행해 결과를 확인한다.
				if (ps.executeUpdate() != 1) {
					throw new IllegalStateException("존재하지 않는 태그입니다: " + row.get("tag_name"));
				}
			}
		}
	}

}
