package com.fivegrade.backend.simulation.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이번 배포에서 입력·평균 계산에 쓸 수 있는 과목 코드 목록(과목 코드 사전의 허용 목록).
 *
 * 과목 코드 사전 전체(숫자 등급 과목 외 포함)가 아니라, 확정된 입력·평균 정책에 맞는 코드만 담긴
 * {@code subject-dictionary/subjects-api-allowlist.json} 을 읽는다.
 * 이 파일은 프론트엔드에 전달된 것과 같은 버전이어야 한다. 버전이 어긋나지 않도록
 * 서버를 시작할 때 파일 내용을 검사하고, 문제가 있으면 서버가 뜨지 않게 한다.
 *
 * 코드 비교는 정확히 일치해야 한다. 공백을 지우거나 대문자로 바꾸는 보정은 하지 않는다.
 */
@Component
public class SubjectCodeAllowlist {

	static final String RESOURCE_PATH = "subject-dictionary/subjects-api-allowlist.json";

	/** 요청의 subjectCode 형식({@code SubjectInput})과 같다. 목록 파일이 다른 형식의 코드를 담지 않게 확인한다. */
	private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z0-9_]{1,50}");

	private final String catalogVersion;
	private final String policyId;
	private final Set<String> allowedCodes;

	public SubjectCodeAllowlist() {
		this(read(RESOURCE_PATH));
	}

	private SubjectCodeAllowlist(AllowlistFile file) {
		this.catalogVersion = requireText(file.catalogVersion(), "catalogVersion");
		this.policyId = requireText(file.policyId(), "policyId");
		this.allowedCodes = requireValidCodes(file.allowedCodes());
	}

	/** 테스트에서 잘못된 목록 파일을 흉내 내기 위한 진입점이다. */
	static SubjectCodeAllowlist parse(InputStream inputStream) {
		return new SubjectCodeAllowlist(parseFile(inputStream));
	}

	/** 목록에 정확히 같은 코드가 있을 때만 true. null 이나 공백이 섞인 값은 false 다. */
	public boolean isAllowed(String subjectCode) {
		return subjectCode != null && allowedCodes.contains(subjectCode);
	}

	public String catalogVersion() {
		return catalogVersion;
	}

	public String policyId() {
		return policyId;
	}

	public int size() {
		return allowedCodes.size();
	}

	private static AllowlistFile read(String resourcePath) {
		try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
			return parseFile(inputStream);
		} catch (IOException e) {
			throw new UncheckedIOException("과목 허용 목록 파일을 읽을 수 없습니다: " + resourcePath, e);
		}
	}

	private static AllowlistFile parseFile(InputStream inputStream) {
		return JsonMapper.builder().build().readValue(inputStream, AllowlistFile.class);
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("과목 허용 목록 파일에 " + fieldName + " 값이 없습니다.");
		}
		return value;
	}

	private static Set<String> requireValidCodes(List<String> codes) {
		if (codes == null || codes.isEmpty()) {
			throw new IllegalStateException("과목 허용 목록 파일에 허용 코드가 없습니다.");
		}
		Set<String> unique = new HashSet<>();
		for (String code : codes) {
			if (code == null || !CODE_FORMAT.matcher(code).matches()) {
				throw new IllegalStateException("과목 허용 목록 파일에 형식이 올바르지 않은 코드가 있습니다.");
			}
			if (!unique.add(code)) {
				throw new IllegalStateException("과목 허용 목록 파일에 중복된 코드가 있습니다: " + code);
			}
		}
		return Set.copyOf(unique);
	}

	/** 목록 파일에서 쓰는 필드만 읽는다. 사용 안내(usage) 등 나머지 필드는 무시한다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record AllowlistFile(String catalogVersion, String policyId, List<String> allowedCodes) {
	}

}
