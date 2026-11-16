package com.fivegrade.backend.holistic.rules;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 학종 서술 분석 규칙 사전. 클래스패스의 {@code holistic/rules-<버전>.json}을 읽어 메모리에 들고 있는다.
 *
 * 규칙·지표·신호 유형은 공개되어 FE가 기기 안 분석에 쓰고, 임계값과 가중치는 이 사전에 두지 않는다(점수 설정은 별도).
 * 규칙의 weight 는 BE 전용이라 공개 응답에 넣지 않는다.
 * 서버를 시작할 때 내용을 검사하고, 문제가 있으면 서버가 뜨지 않게 한다. 잘못된 사전이 조용히 배포되는 것을 막기 위해서다.
 *
 * 규칙 정규식은 FE(JavaScript)와 BE(Java)가 같은 결과를 내야 하므로 두 방언에 공통인 부분집합만 허용한다.
 */
@Component
public class HolisticRuleCatalog {

	static final String RESOURCE_PATH = "holistic/rules-0.3.0.json";

	/** 영역 코드. FE 분석의 영역 이름과 같다. */
	public static final Set<String> SECTIONS = Set.of("autonomy", "club", "career", "subject", "behavior");

	/** 평가요소 코드 */
	public static final Set<String> ELEMENTS = Set.of("ACADEMIC", "MAJOR_FIT", "POTENTIAL", "COMMUNITY");

	/** 규칙마다 필요한 긍정·부정 예시 최소 개수. 이 예시가 자동 테스트가 된다. */
	static final int MIN_EXAMPLES = 3;

	/**
	 * JS와 Java가 다르게 해석하거나 한쪽만 지원하는 정규식 구문. 룩어라운드, 이름 있는 그룹, 유니코드 속성,
	 * 단어 경계(한글에서 결과가 다름), 인라인 플래그, 소유 수량자, Java 전용 이스케이프를 막는다.
	 */
	private static final Pattern FORBIDDEN_SYNTAX = Pattern.compile(
			"\\(\\?[<=!i-]|\\\\[pPbBkKhHRGAZzXQE]|[*+?}]\\+");

	private final String rulesVersion;
	private final List<String> acceptedPreviousVersions;
	private final Limits limits;
	private final List<String> types;
	private final List<Indicator> indicators;
	private final List<Rule> rules;
	private final String contentHash;

	public HolisticRuleCatalog() {
		this(readResource(RESOURCE_PATH));
	}

	private HolisticRuleCatalog(byte[] content) {
		RulesFile file = parseFile(new ByteArrayInputStream(content));
		this.rulesVersion = requireText(file.rulesVersion(), "rulesVersion");
		this.acceptedPreviousVersions = file.acceptedPreviousVersions() == null
				? List.of() : List.copyOf(file.acceptedPreviousVersions());
		this.limits = requireLimits(file.limits());
		this.types = requireTypes(file.types());
		this.indicators = requireIndicators(file.indicators());
		this.rules = requireRules(file.rules(), Set.copyOf(types), indicatorIds(indicators));
		this.contentHash = hashOf(content);
	}

	/** 테스트에서 잘못된 사전 파일을 흉내 내기 위한 진입점이다. */
	static HolisticRuleCatalog parse(String json) {
		return new HolisticRuleCatalog(json.getBytes(StandardCharsets.UTF_8));
	}

	public String rulesVersion() {
		return rulesVersion;
	}

	/** 서버가 받아 주는 이전 규칙 버전. FE 배포 시차를 위해 직전 버전 하나까지 둔다. */
	public List<String> acceptedPreviousVersions() {
		return acceptedPreviousVersions;
	}

	/** 현재 버전과 허용하는 이전 버전에 해당하는지 */
	public boolean isAcceptedVersion(String version) {
		return version != null && (rulesVersion.equals(version) || acceptedPreviousVersions.contains(version));
	}

	public Limits limits() {
		return limits;
	}

	public List<String> types() {
		return types;
	}

	public List<Indicator> indicators() {
		return indicators;
	}

	public List<Rule> rules() {
		return rules;
	}

	public boolean hasIndicator(String id) {
		return indicators.stream().anyMatch(indicator -> indicator.id().equals(id));
	}

	public boolean hasType(String type) {
		return types.contains(type);
	}

	/** 사전 내용이 바뀌면 달라지는 값. 응답 ETag 에 쓴다. */
	public String contentHash() {
		return contentHash;
	}

	/** 한도 값. 요청 검증과 FE 분석이 같은 숫자를 쓴다. */
	public record Limits(
			int evidenceMax,
			int evidenceTextMaxChars,
			int entryEvidenceMax,
			int minNarrativeChars,
			int indicatorSignalsMax,
			int interestTagsMax,
			int totalSectionCharsMax
	) {
	}

	public record Indicator(String id, String label, String element, List<String> sections) {
	}

	/**
	 * 규칙 한 개.
	 *
	 * @param weight   BE 전용 가중치. 공개 응답에 내리지 않는다.
	 * @param examples 긍정·부정 예시. 자동 테스트 입력이며 공개 응답에 내리지 않는다.
	 */
	public record Rule(
			String id,
			String indicator,
			List<String> types,
			List<String> sections,
			String pattern,
			Integer within,
			List<String> excludes,
			double weight,
			Examples examples,
			String source
	) {
	}

	public record Examples(List<String> positive, List<String> negative) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RulesFile(
			String rulesVersion,
			List<String> acceptedPreviousVersions,
			Limits limits,
			List<String> types,
			List<Indicator> indicators,
			List<Rule> rules
	) {
	}

	private static byte[] readResource(String resourcePath) {
		try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
			return inputStream.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("규칙 사전 파일을 읽을 수 없습니다: " + resourcePath, e);
		}
	}

	private static RulesFile parseFile(InputStream inputStream) {
		return JsonMapper.builder().build().readValue(inputStream, RulesFile.class);
	}

	private static Limits requireLimits(Limits limits) {
		if (limits == null) {
			throw new IllegalStateException("규칙 사전에 limits 가 없습니다");
		}
		int[] values = {
				limits.evidenceMax(), limits.evidenceTextMaxChars(), limits.entryEvidenceMax(),
				limits.minNarrativeChars(), limits.indicatorSignalsMax(), limits.interestTagsMax(),
				limits.totalSectionCharsMax()
		};
		for (int value : values) {
			if (value <= 0) {
				throw new IllegalStateException("규칙 사전 limits 값은 모두 양수여야 합니다");
			}
		}
		return limits;
	}

	private static List<String> requireTypes(List<String> types) {
		if (types == null || types.isEmpty()) {
			throw new IllegalStateException("규칙 사전에 신호 유형이 없습니다");
		}
		if (new HashSet<>(types).size() != types.size()) {
			throw new IllegalStateException("규칙 사전의 신호 유형이 중복되었습니다");
		}
		return List.copyOf(types);
	}

	private static List<Indicator> requireIndicators(List<Indicator> indicators) {
		if (indicators == null || indicators.isEmpty()) {
			throw new IllegalStateException("규칙 사전에 지표가 없습니다");
		}
		Set<String> ids = new HashSet<>();
		for (Indicator indicator : indicators) {
			requireText(indicator.id(), "indicator.id");
			if (!ids.add(indicator.id())) {
				throw new IllegalStateException("지표 ID가 중복되었습니다: " + indicator.id());
			}
			if (!ELEMENTS.contains(indicator.element())) {
				throw new IllegalStateException("지표의 평가요소가 올바르지 않습니다: " + indicator.id());
			}
			requireSections(indicator.sections(), "지표 " + indicator.id());
		}
		return List.copyOf(indicators);
	}

	private static Set<String> indicatorIds(List<Indicator> indicators) {
		Set<String> ids = new HashSet<>();
		for (Indicator indicator : indicators) {
			ids.add(indicator.id());
		}
		return ids;
	}

	private static List<Rule> requireRules(List<Rule> rules, Set<String> typeSet, Set<String> indicatorIds) {
		if (rules == null || rules.isEmpty()) {
			throw new IllegalStateException("규칙 사전에 규칙이 없습니다");
		}
		Set<String> ids = new HashSet<>();
		for (Rule rule : rules) {
			String id = requireText(rule.id(), "rule.id");
			if (!ids.add(id)) {
				throw new IllegalStateException("규칙 ID가 중복되었습니다: " + id);
			}
			if (!indicatorIds.contains(rule.indicator())) {
				throw new IllegalStateException("규칙의 지표가 사전에 없습니다: " + id);
			}
			if (rule.types() == null || rule.types().isEmpty() || !typeSet.containsAll(rule.types())) {
				throw new IllegalStateException("규칙의 신호 유형이 올바르지 않습니다: " + id);
			}
			requireSections(rule.sections(), "규칙 " + id);
			requireRegex(rule.pattern(), id);
			if (rule.excludes() != null) {
				for (String exclude : rule.excludes()) {
					requireRegex(exclude, id);
				}
			}
			if (rule.weight() <= 0) {
				throw new IllegalStateException("규칙 가중치는 양수여야 합니다: " + id);
			}
			if (rule.examples() == null
					|| rule.examples().positive() == null || rule.examples().positive().size() < MIN_EXAMPLES
					|| rule.examples().negative() == null || rule.examples().negative().size() < MIN_EXAMPLES) {
				throw new IllegalStateException("규칙에는 긍정·부정 예시가 각각 " + MIN_EXAMPLES + "개 이상 필요합니다: " + id);
			}
			requireText(rule.source(), "rule.source (" + id + ")");
		}
		return rules.stream().map(HolisticRuleCatalog::withNonNullExcludes).toList();
	}

	private static Rule withNonNullExcludes(Rule rule) {
		if (rule.excludes() != null) {
			return rule;
		}
		return new Rule(rule.id(), rule.indicator(), rule.types(), rule.sections(), rule.pattern(),
				rule.within(), List.of(), rule.weight(), rule.examples(), rule.source());
	}

	private static void requireSections(List<String> sections, String owner) {
		if (sections == null || sections.isEmpty() || !SECTIONS.containsAll(sections)) {
			throw new IllegalStateException(owner + "의 영역이 올바르지 않습니다");
		}
	}

	private static void requireRegex(String regex, String ruleId) {
		requireText(regex, "pattern (" + ruleId + ")");
		if (FORBIDDEN_SYNTAX.matcher(regex).find()) {
			throw new IllegalStateException("JS와 Java에서 결과가 달라질 수 있는 정규식 구문입니다: " + ruleId);
		}
		try {
			Pattern.compile(regex);
		} catch (PatternSyntaxException e) {
			throw new IllegalStateException("정규식을 컴파일할 수 없습니다: " + ruleId, e);
		}
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("규칙 사전의 " + name + " 값이 비어 있습니다");
		}
		return value;
	}

	private static String hashOf(byte[] content) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
			return HexFormat.of().formatHex(digest).substring(0, 12);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 은 모든 자바 구현이 지원해야 하는 알고리즘이라 여기에 오지 않는다.
			throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
		}
	}

}
