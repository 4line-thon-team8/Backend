package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 전공 키워드 사전(tag_keywords) 테이블과 시드.
 *
 * GET /api/tag-map 이 내려주는 "키워드 → 태그" 사전이다. FE 는 이 사전과 사용자가 입력한 텍스트를
 * 기기 안에서 대조해 전공 키워드 태그를 만든다. 키워드는 tags 의 이름(자연키)으로 태그를 가리킨다.
 *
 * 데이터는 src/main/resources/seed-data/tag-keywords.csv 에 있고, 이 클래스는 읽어서 넣기만 한다.
 * 이미 적용된 마이그레이션이므로 CSV 를 고치지 않는다. 키워드를 바꾸거나 늘릴 때는 새 버전(V번호)으로 추가한다.
 * (CSV 가 바뀌면 체크섬이 달라져 Flyway 가 시작을 막는다.)
 */
public class V10__seed_tag_keywords extends BaseJavaMigration {

	private static final String TAG_KEYWORDS_CSV = "seed-data/tag-keywords.csv";

	@Override
	public Integer getChecksum() {
		return SeedCsv.checksum(TAG_KEYWORDS_CSV);
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		createTable(connection);
		insertKeywords(connection);
	}

	private void createTable(Connection connection) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE tag_keywords (
					    keyword VARCHAR(50) NOT NULL,
					    tag_id  BIGINT      NOT NULL REFERENCES tags (id),
					    PRIMARY KEY (keyword, tag_id)
					)
					""");
			statement.execute("CREATE INDEX idx_tag_keywords_tag_id ON tag_keywords (tag_id)");
		}
	}

	private void insertKeywords(Connection connection) throws Exception {
		String sql = "INSERT INTO tag_keywords (keyword, tag_id) SELECT ?, id FROM tags WHERE name = ?";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			for (Map<String, String> row : SeedCsv.read(TAG_KEYWORDS_CSV)) {
				ps.setString(1, row.get("keyword"));
				ps.setString(2, row.get("tag_name"));
				// 태그 이름이 없으면 SELECT 결과가 0행이라 조용히 빠지므로, 건별로 실행해 결과를 확인한다.
				if (ps.executeUpdate() != 1) {
					throw new IllegalStateException("존재하지 않는 태그입니다: " + row.get("tag_name"));
				}
			}
		}
	}

}
