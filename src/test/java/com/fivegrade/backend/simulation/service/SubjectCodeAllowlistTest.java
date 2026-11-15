package com.fivegrade.backend.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 과목 허용 목록 파일(과목 코드 사전 1.0.0-rc.3)을 제대로 읽고, 잘못된 파일은 거부하는지 확인한다.
 * Spring 컨텍스트 없이 실행한다.
 */
class SubjectCodeAllowlistTest {

	/** 전달받은 manifest.json 에 적힌 허용 목록 파일의 SHA-256. 파일이 바뀌면 이 값도 같이 바꿔야 한다. */
	private static final String EXPECTED_SHA256 = "dfd99d22d31a1a846b5689ea3d479a63e54f036dd2cfb698a12334a960ebcb42";

	private final SubjectCodeAllowlist allowlist = new SubjectCodeAllowlist();

	@Test
	void 기본_목록은_정책_버전과_72개_코드를_담는다() {
		assertThat(allowlist.catalogVersion()).isEqualTo("1.0.0-rc.3");
		assertThat(allowlist.policyId()).isEqualTo("CORE_SUBJECTS_UNWEIGHTED_V1");
		assertThat(allowlist.size()).isEqualTo(72);
	}

	@Test
	void 목록_파일은_전달받은_파일과_내용이_같다() throws IOException, NoSuchAlgorithmException {
		byte[] bytes;
		try (InputStream inputStream = new ClassPathResource(SubjectCodeAllowlist.RESOURCE_PATH).getInputStream()) {
			bytes = inputStream.readAllBytes();
		}

		// 전달받은 해시는 LF 줄바꿈 기준이다. Windows 에서 CRLF 로 저장돼도 같은 내용이면 통과해야 한다.
		String normalized = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
		String actual = HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));

		assertThat(actual).isEqualTo(EXPECTED_SHA256);
	}

	@Test
	void 목록에_있는_코드만_허용한다() {
		assertThat(allowlist.isAllowed("SUBJ_0001")).isTrue();
		assertThat(allowlist.isAllowed("SUBJ_0029")).isTrue();
		assertThat(allowlist.isAllowed("SUBJ_0086")).isTrue();
	}

	@Test
	void 목록_사이_빈_번호와_범위_밖_코드는_허용하지_않는다() {
		// 0030~0035 와 0071~0078 은 이번 평균 정책에 포함되지 않은 번호다.
		assertThat(allowlist.isAllowed("SUBJ_0030")).isFalse();
		assertThat(allowlist.isAllowed("SUBJ_0071")).isFalse();
		assertThat(allowlist.isAllowed("SUBJ_0087")).isFalse();
	}

	@Test
	void 대소문자와_공백을_보정하지_않는다() {
		assertThat(allowlist.isAllowed("subj_0001")).isFalse();
		assertThat(allowlist.isAllowed(" SUBJ_0001")).isFalse();
		assertThat(allowlist.isAllowed("SUBJ_0001 ")).isFalse();
		assertThat(allowlist.isAllowed("")).isFalse();
		assertThat(allowlist.isAllowed(null)).isFalse();
	}

	@Test
	void 필수_필드가_없는_파일은_거부한다() {
		assertInvalid("""
				{"policyId": "P", "allowedCodes": ["SUBJ_0001"]}""");
		assertInvalid("""
				{"catalogVersion": "1", "allowedCodes": ["SUBJ_0001"]}""");
		assertInvalid("""
				{"catalogVersion": "1", "policyId": "P"}""");
	}

	@Test
	void 코드가_비었거나_형식이_틀리거나_중복되면_거부한다() {
		assertInvalid("""
				{"catalogVersion": "1", "policyId": "P", "allowedCodes": []}""");
		assertInvalid("""
				{"catalogVersion": "1", "policyId": "P", "allowedCodes": ["subj_0001"]}""");
		assertInvalid("""
				{"catalogVersion": "1", "policyId": "P", "allowedCodes": ["SUBJ_0001", "SUBJ_0001"]}""");
	}

	@Test
	void 사용_안내_같은_다른_필드는_무시하고_읽는다() {
		SubjectCodeAllowlist parsed = parse("""
				{"catalogVersion": "1", "policyId": "P", "usage": "안내 문구", "allowedCodes": ["SUBJ_0001"]}""");

		assertThat(parsed.size()).isEqualTo(1);
		assertThat(parsed.isAllowed("SUBJ_0001")).isTrue();
	}

	private static void assertInvalid(String json) {
		assertThatThrownBy(() -> parse(json)).isInstanceOf(IllegalStateException.class);
	}

	private static SubjectCodeAllowlist parse(String json) {
		return SubjectCodeAllowlist.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

}
