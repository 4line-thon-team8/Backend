package com.fivegrade.backend.holistic.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 규칙 사전의 회귀 점검. 라벨이 붙은 문장(corpus.tsv)에 규칙을 돌려 지표별 정밀도·재현율을 계산한다.
 *
 * <p>코퍼스는 직접 작성한 문장이며 규칙을 다듬는 과정에서 함께 조정되었다. 따라서 이 수치는 실제 생활기록부에서의
 * 성능이 아니라 "규칙을 고치다가 기존 동작이 깨지지 않았는가"를 보는 회귀 지표다. 임계값은 현재 수치에서 약간 여유를
 * 둔 값이며, 실제 데이터로 검증하면서 올려 간다.
 */
class HolisticRuleCorpusTest {

	private static final double MIN_PRECISION = 0.90;
	private static final double MIN_RECALL = 0.90;

	private final HolisticRuleCatalog catalog = new HolisticRuleCatalog();

	@Test
	void 전체_정밀도와_재현율이_기준_이상이다() throws IOException {
		Map<String, int[]> counts = new TreeMap<>(); // [tp, fp, fn]
		List<String> misses = new ArrayList<>();

		for (Sample sample : load()) {
			Set<String> got = detect(sample);
			Set<String> all = new TreeSet<>(got);
			all.addAll(sample.expected());
			for (String indicator : all) {
				int[] c = counts.computeIfAbsent(indicator, k -> new int[3]);
				boolean g = got.contains(indicator);
				boolean e = sample.expected().contains(indicator);
				if (g && e) {
					c[0]++;
				} else if (g) {
					c[1]++;
					misses.add("FP " + indicator + " | " + sample.text());
				} else {
					c[2]++;
					misses.add("FN " + indicator + " | " + sample.text());
				}
			}
		}

		int tp = counts.values().stream().mapToInt(c -> c[0]).sum();
		int fp = counts.values().stream().mapToInt(c -> c[1]).sum();
		int fn = counts.values().stream().mapToInt(c -> c[2]).sum();
		double precision = tp / (double) (tp + fp);
		double recall = tp / (double) (tp + fn);

		assertThat(precision).as("정밀도 (오탐 목록: %s)", misses).isGreaterThanOrEqualTo(MIN_PRECISION);
		assertThat(recall).as("재현율 (누락 목록: %s)", misses).isGreaterThanOrEqualTo(MIN_RECALL);
	}

	@Test
	void 코퍼스는_규칙이_있는_모든_지표를_한_번_이상_긍정으로_다룬다() throws IOException {
		Set<String> covered = new TreeSet<>();
		for (Sample sample : load()) {
			covered.addAll(sample.expected());
		}
		Set<String> withRules = new TreeSet<>();
		catalog.rules().forEach(rule -> withRules.add(rule.indicator()));

		assertThat(covered).containsAll(withRules);
	}

	private Set<String> detect(Sample sample) {
		Set<String> got = new TreeSet<>();
		for (HolisticRuleCatalog.Rule rule : catalog.rules()) {
			if (!rule.sections().contains(sample.section())) {
				continue;
			}
			boolean matched = Pattern.compile(rule.pattern()).matcher(sample.text()).find();
			boolean excluded = rule.excludes().stream()
					.anyMatch(regex -> Pattern.compile(regex).matcher(sample.text()).find());
			if (matched && !excluded) {
				got.add(rule.indicator());
			}
		}
		return got;
	}

	private List<Sample> load() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/holistic/corpus.tsv")) {
			assertThat(in).as("corpus.tsv").isNotNull();
			String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			List<Sample> samples = new ArrayList<>();
			for (String line : content.split("\n")) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] cols = line.split("\t", -1);
				Set<String> expected = new TreeSet<>();
				if (cols.length > 2 && !cols[2].isBlank()) {
					expected.addAll(Arrays.asList(cols[2].trim().split(",")));
				}
				samples.add(new Sample(cols[0], cols[1], expected));
			}
			return samples;
		}
	}

	private record Sample(String section, String text, Set<String> expected) {
	}
}
