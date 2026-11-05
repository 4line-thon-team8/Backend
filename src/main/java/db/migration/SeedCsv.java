package db.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * 시드 마이그레이션이 classpath 의 CSV 를 읽을 때 쓰는 도우미.
 * 마이그레이션은 한 번 적용되면 바뀌면 안 되므로, 앱의 다른 코드에 의존하지 않도록 이 패키지 안에 둔다.
 *
 * - 첫 줄은 헤더이고, 각 행은 {헤더 이름: 값} 으로 읽는다.
 * - 쉼표가 들어간 값은 큰따옴표로 감싼 CSV 규칙을 지원한다.
 * - 빈 값은 null 로 돌려준다.
 */
final class SeedCsv {

	private SeedCsv() {
	}

	static List<Map<String, String>> read(String path) {
		List<List<String>> records = parse(readText(path));
		List<String> header = records.getFirst();
		List<Map<String, String>> rows = new ArrayList<>();
		for (List<String> record : records.subList(1, records.size())) {
			if (record.size() != header.size()) {
				throw new IllegalStateException(path + ": 열 개수가 헤더와 다릅니다: " + record);
			}
			Map<String, String> row = new LinkedHashMap<>();
			for (int i = 0; i < header.size(); i++) {
				String value = record.get(i);
				row.put(header.get(i), value.isEmpty() ? null : value);
			}
			rows.add(row);
		}
		return rows;
	}

	/** CSV 파일 내용이 바뀌면 값이 달라지는 체크섬. Flyway 가 적용된 시드가 몰래 바뀐 것을 잡는 데 쓴다. */
	static int checksum(String... paths) {
		CRC32 crc = new CRC32();
		for (String path : paths) {
			crc.update(readText(path).getBytes(StandardCharsets.UTF_8));
		}
		return (int) crc.getValue();
	}

	private static String readText(String path) {
		try (InputStream in = SeedCsv.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("시드 파일을 찾을 수 없습니다: " + path);
			}
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			// BOM 과 줄바꿈 방식(CRLF)이 달라도 같은 내용으로 취급한다.
			if (text.startsWith("﻿")) {
				text = text.substring(1);
			}
			return text.replace("\r\n", "\n");
		} catch (IOException e) {
			throw new IllegalStateException("시드 파일을 읽지 못했습니다: " + path, e);
		}
	}

	private static List<List<String>> parse(String text) {
		List<List<String>> records = new ArrayList<>();
		List<String> record = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				} else if (c == '"') {
					quoted = false;
				} else {
					field.append(c);
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == ',') {
				record.add(field.toString());
				field.setLength(0);
			} else if (c == '\n') {
				record.add(field.toString());
				field.setLength(0);
				records.add(record);
				record = new ArrayList<>();
			} else {
				field.append(c);
			}
		}
		if (field.length() > 0 || !record.isEmpty()) {
			record.add(field.toString());
			records.add(record);
		}
		return records;
	}

}
