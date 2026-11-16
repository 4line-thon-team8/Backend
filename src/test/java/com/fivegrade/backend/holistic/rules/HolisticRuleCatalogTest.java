package com.fivegrade.backend.holistic.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 규칙 사전의 로드와 검증을 확인한다. DB나 Spring 없이 실행한다.
 * 실제 사전 파일의 모든 예시를 규칙에 돌려서, 규칙이 자기 예시와 어긋나지 않게 한다.
 */
class HolisticRuleCatalogTest {

	private final HolisticRuleCatalog catalog = new HolisticRuleCatalog();

	@Test
	void 실제_사전이_로드되고_기본_구성을_갖는다() {
		assertThat(catalog.rulesVersion()).isEqualTo("0.1.0");
		assertThat(catalog.types()).hasSize(10);
		assertThat(catalog.indicators()).isNotEmpty();
		assertThat(catalog.rules()).isNotEmpty();
		assertThat(catalog.limits().evidenceMax()).isEqualTo(150);
		assertThat(catalog.limits().evidenceTextMaxChars()).isEqualTo(30);
		assertThat(catalog.contentHash()).hasSize(12);
	}

	@Test
	void 현재_버전만_받아_준다() {
		assertThat(catalog.isAcceptedVersion("0.1.0")).isTrue();
		assertThat(catalog.isAcceptedVersion("0.0.9")).isFalse();
		assertThat(catalog.isAcceptedVersion(null)).isFalse();
	}

	@Test
	void 모든_규칙은_자기_예시와_일치한다() {
		for (HolisticRuleCatalog.Rule rule : catalog.rules()) {
			for (String text : rule.examples().positive()) {
				assertThat(matches(rule, text)).as("%s 긍정 예시: %s", rule.id(), text).isTrue();
			}
			for (String text : rule.examples().negative()) {
				assertThat(matches(rule, text)).as("%s 부정 예시: %s", rule.id(), text).isFalse();
			}
		}
	}

	@Test
	void 모든_규칙은_예시를_각각_세_개_이상_갖고_출처가_있다() {
		for (HolisticRuleCatalog.Rule rule : catalog.rules()) {
			assertThat(rule.examples().positive()).as(rule.id()).hasSizeGreaterThanOrEqualTo(3);
			assertThat(rule.examples().negative()).as(rule.id()).hasSizeGreaterThanOrEqualTo(3);
			assertThat(rule.source()).as(rule.id()).isNotBlank();
		}
	}

	@Test
	void 규칙의_지표와_유형은_사전에_있다() {
		for (HolisticRuleCatalog.Rule rule : catalog.rules()) {
			assertThat(catalog.hasIndicator(rule.indicator())).as(rule.id()).isTrue();
			assertThat(catalog.types()).containsAll(rule.types());
		}
	}

	@Test
	void 중복_규칙_ID는_거부한다() {
		String json = catalogJson(rule("R1", "A3", "[\"ROLE\"]", "역할", 3, 3)
				+ "," + rule("R1", "A3", "[\"ROLE\"]", "역할", 3, 3));

		assertThatThrownBy(() -> HolisticRuleCatalog.parse(json)).hasMessageContaining("중복");
	}

	@Test
	void 사전에_없는_지표를_쓰는_규칙은_거부한다() {
		String json = catalogJson(rule("R1", "ZZ", "[\"ROLE\"]", "역할", 3, 3));

		assertThatThrownBy(() -> HolisticRuleCatalog.parse(json)).hasMessageContaining("지표");
	}

	@Test
	void 사전에_없는_신호_유형을_쓰는_규칙은_거부한다() {
		String json = catalogJson(rule("R1", "A3", "[\"UNKNOWN\"]", "역할", 3, 3));

		assertThatThrownBy(() -> HolisticRuleCatalog.parse(json)).hasMessageContaining("신호 유형");
	}

	@Test
	void 예시가_세_개_미만이면_거부한다() {
		assertThatThrownBy(() -> HolisticRuleCatalog.parse(catalogJson(rule("R1", "A3", "[\"ROLE\"]", "역할", 2, 3))))
				.hasMessageContaining("예시");
		assertThatThrownBy(() -> HolisticRuleCatalog.parse(catalogJson(rule("R1", "A3", "[\"ROLE\"]", "역할", 3, 2))))
				.hasMessageContaining("예시");
	}

	@Test
	void 컴파일되지_않는_정규식은_거부한다() {
		String json = catalogJson(rule("R1", "A3", "[\"ROLE\"]", "(역할", 3, 3));

		assertThatThrownBy(() -> HolisticRuleCatalog.parse(json)).hasMessageContaining("컴파일");
	}

	@Test
	void JS와_Java에서_결과가_달라질_수_있는_구문은_거부한다() {
		// 룩어헤드, 룩비하인드, 이름 있는 그룹, 인라인 플래그, 유니코드 속성, 단어 경계, 소유 수량자
		String[] forbidden = {
				"역할(?=을)", "(?<=팀)역할", "(?<n>역할)", "(?i)role", "\\\\p{L}+", "\\\\b역할", "역할++"
		};
		for (String pattern : forbidden) {
			String json = catalogJson(rule("R1", "A3", "[\"ROLE\"]", pattern, 3, 3));

			assertThatThrownBy(() -> HolisticRuleCatalog.parse(json))
					.as(pattern)
					.hasMessageContaining("구문");
		}
	}

	@Test
	void 공통_부분집합_구문은_허용한다() {
		String json = catalogJson(rule("R1", "A3", "[\"ROLE\"]", "(역할|임무)을?\\\\s*(나누|맡)+", 3, 3));

		assertThat(HolisticRuleCatalog.parse(json).rules()).hasSize(1);
	}

	private boolean matches(HolisticRuleCatalog.Rule rule, String text) {
		boolean matched = Pattern.compile(rule.pattern()).matcher(text).find();
		boolean excluded = rule.excludes().stream().anyMatch(regex -> Pattern.compile(regex).matcher(text).find());
		return matched && !excluded;
	}

	/** 지표 하나와 주어진 규칙들만 담은 최소 사전 JSON */
	private static String catalogJson(String rulesJson) {
		return """
				{"rulesVersion": "9.9.9",
				 "limits": {"evidenceMax": 150, "evidenceTextMaxChars": 30, "entryEvidenceMax": 5,
				            "minNarrativeChars": 300, "indicatorSignalsMax": 20, "interestTagsMax": 30,
				            "totalSectionCharsMax": 200000},
				 "types": ["ROLE", "METHOD"],
				 "indicators": [{"id": "A3", "label": "탐구력", "element": "ACADEMIC", "sections": ["club"]}],
				 "rules": [%s]}""".formatted(rulesJson);
	}

	private static String rule(String id, String indicator, String types, String pattern, int positive, int negative) {
		return """
				{"id": "%s", "indicator": "%s", "types": %s, "sections": ["club"],
				 "pattern": "%s", "excludes": [], "weight": 1.0,
				 "examples": {"positive": %s, "negative": %s}, "source": "테스트"}"""
				.formatted(id, indicator, types, pattern, examples(positive), examples(negative));
	}

	private static String examples(int count) {
		StringBuilder builder = new StringBuilder("[");
		for (int i = 0; i < count; i++) {
			builder.append(i == 0 ? "" : ",").append("\"예시").append(i).append('"');
		}
		return builder.append(']').toString();
	}

}
