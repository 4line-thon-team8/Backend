package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticNarrativeFacts.MajorFacts;
import com.fivegrade.backend.holistic.report.HolisticReportInput.ElementView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.IndicatorView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotMeasured;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotObserved;
import com.fivegrade.backend.holistic.report.ParsedNarrative.MajorLine;
import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI가 쓴 활동 요약·학과별 조언 초안을 서버가 확정한 사실 자료와 대조한다. 순수 Java 클래스다.
 *
 * 글 하나씩 판단한다. 어긋난 글은 버리고 그 글만 기본 문장으로 대신한다(통과한 글은 그대로 쓴다).
 * 믿는 것은 (1) 근거 값이 입력에 있는지, (2) 금지 표현이 없는지, (3) 문장에 나온 지표 이름이 근거의 이름인지,
 * (4) 따옴표로 인용한 구절이 서버가 넘긴 근거 구절에 있는지다. 문장 내용 자체는 믿지 않는다.
 */
public final class HolisticNarrativeValidator {

	static final int MAX_SUMMARY_LENGTH = 200;
	static final int MAX_ADVICE_LENGTH = 160;
	static final int MAX_SENTENCES = 2;

	private static final Pattern FORBIDDEN = Pattern.compile(
			"\\d|https?:|www\\.|@|%|합격|불합격|보장|확실|반드시|무조건|확률|점수|등급|입결|경쟁률|대학|학과|전형|"
					+ "우울|장애|진단|성격|"
					+ "부족|없어요|없다|없는|없음|못해|못하|미흡|약해|약하|낮아|낮은|뒤처|떨어지");

	/** 줄바꿈과 목록 기호. 태그 이름에 쓰는 가운뎃점(·)은 허용한다. */
	private static final Pattern LINE_BREAK = Pattern.compile("[\\r\\n•\\-*#]");

	private static final Pattern QUOTE = Pattern.compile("[「『“\"'‘]([^」』”\"'’]*)[」』”\"'’]");

	private HolisticNarrativeValidator() {
	}

	/**
	 * 검증을 통과한 글.
	 *
	 * @param summary    통과한 활동 요약. 없거나 통과하지 못했으면 null
	 * @param advices    통과한 학과별 조언(학과 코드 → 글)
	 * @param rejections 버린 글의 사유 코드. 로그에는 이 고정 코드만 남긴다.
	 */
	public record Checked(Line summary, Map<String, Line> advices, List<String> rejections) {
	}

	public static Checked check(HolisticNarrativeFacts facts, ParsedNarrative parsed) {
		Catalog catalog = new Catalog(facts);
		List<String> rejections = new ArrayList<>();

		Line summary = null;
		if (facts.canSummarize()) {
			String problem = checkLine(parsed.summary(), catalog, facts, null, MAX_SUMMARY_LENGTH);
			if (problem == null) {
				summary = parsed.summary();
			} else {
				rejections.add("SUMMARY_" + problem);
			}
		}

		Map<String, Line> advices = new LinkedHashMap<>();
		Map<String, MajorFacts> majors = new HashMap<>();
		facts.majors().forEach(major -> majors.put(major.code(), major));
		Set<String> seen = new HashSet<>();
		for (MajorLine item : parsed.majorAdvices()) {
			MajorFacts major = majors.get(item.majorCode());
			if (major == null) {
				rejections.add("ADVICE_UNKNOWN_MAJOR");
				continue;
			}
			if (!seen.add(item.majorCode())) {
				rejections.add("ADVICE_DUPLICATE_MAJOR");
				advices.remove(item.majorCode());
				continue;
			}
			String problem = checkLine(item.line(), catalog, facts, major, MAX_ADVICE_LENGTH);
			if (problem == null) {
				advices.put(item.majorCode(), item.line());
			} else {
				rejections.add("ADVICE_" + problem);
			}
		}
		return new Checked(summary, advices, rejections);
	}

	private static String checkLine(Line line, Catalog catalog, HolisticNarrativeFacts facts, MajorFacts major,
			int maxLength) {
		String text = line.text() == null ? "" : line.text().strip();
		if (text.isEmpty() || text.length() > maxLength) {
			return "TEXT_LENGTH";
		}
		if (LINE_BREAK.matcher(text).find()) {
			return "FORMAT";
		}
		if (FORBIDDEN.matcher(text).find()) {
			return "FORBIDDEN";
		}
		if (!isHaeyo(text)) {
			return "STYLE";
		}
		String quote = checkQuotes(text, facts.evidencePhrases());
		if (quote != null) {
			return quote;
		}
		if (line.refs().isEmpty()) {
			return "NO_REFS";
		}

		Set<String> allowedLabels = new LinkedHashSet<>();
		for (String ref : line.refs()) {
			Set<String> labels = catalog.refLabels.get(ref);
			boolean majorTag = major != null && major.tags().contains(ref);
			if (labels == null && !majorTag) {
				return "UNKNOWN_REF";
			}
			if (labels != null) {
				allowedLabels.addAll(labels);
			}
		}
		for (NotMeasured notMeasured : facts.report().notMeasured()) {
			if (text.contains(notMeasured.label())) {
				return "NOT_MEASURED";
			}
		}
		for (String label : catalog.allLabels) {
			if (text.contains(label) && !allowedLabels.contains(label)) {
				return "LABEL_MISMATCH";
			}
		}
		return null;
	}

	/** 모든 문장이 "요."로 끝나는 해요체이고 문장 수가 한도 이내여야 한다. */
	private static boolean isHaeyo(String text) {
		if (!text.endsWith("요.")) {
			return false;
		}
		String[] sentences = text.split("\\.");
		int count = 0;
		for (String sentence : sentences) {
			String stripped = sentence.strip();
			if (stripped.isEmpty()) {
				continue;
			}
			if (!stripped.endsWith("요")) {
				return false;
			}
			count++;
		}
		return count >= 1 && count <= MAX_SENTENCES;
	}

	/** 따옴표로 인용한 구절은 서버가 넘긴 근거 구절 안에 있어야 한다. 근거 구절이 없으면 인용 자체를 받지 않는다. */
	private static String checkQuotes(String text, List<String> evidence) {
		Matcher matcher = QUOTE.matcher(text);
		while (matcher.find()) {
			String quoted = matcher.group(1).strip();
			if (quoted.isEmpty() || evidence.stream().noneMatch(phrase -> phrase.contains(quoted))) {
				return "QUOTE";
			}
		}
		return null;
	}

	/** 근거로 쓸 수 있는 값과 그 값이 허용하는 이름들 */
	private static final class Catalog {
		final Map<String, Set<String>> refLabels = new HashMap<>();
		final Set<String> allLabels = new LinkedHashSet<>();

		Catalog(HolisticNarrativeFacts facts) {
			for (ElementView element : facts.report().elements()) {
				Set<String> elementLabels = new LinkedHashSet<>();
				elementLabels.add(element.label());
				allLabels.add(element.label());
				for (IndicatorView indicator : element.indicators()) {
					elementLabels.add(indicator.label());
					allLabels.add(indicator.label());
					refLabels.put(indicator.id(), Set.of(indicator.label()));
				}
				// 관찰되지 않은 지표는 이름만 보호하고 근거로는 받지 않는다.
				for (NotObserved indicator : element.notObserved()) {
					allLabels.add(indicator.label());
				}
				refLabels.put(element.id(), elementLabels);
			}
			facts.report().notMeasured().forEach(item -> allLabels.remove(item.label()));
			facts.report().interestTags().forEach(tag -> refLabels.putIfAbsent(tag, Set.of()));
		}
	}
}
