package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticNarrativeFacts.MajorFacts;
import com.fivegrade.backend.holistic.report.HolisticReportInput.ElementView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.IndicatorView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotMeasured;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotObserved;
import com.fivegrade.backend.holistic.report.ParsedNarrative.EvidenceLine;
import com.fivegrade.backend.holistic.report.ParsedNarrative.MajorLine;
import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI가 쓴 활동 요약·학과별 조언 초안을 서버가 확정한 사실 자료와 대조한다. 순수 Java 클래스다.
 *
 * 글 하나씩 판단한다. 어긋난 글은 버리고 그 글만 기본 문장으로 대신한다(통과한 글은 그대로 쓴다).
 * 믿는 것은 (1) 근거 값이 입력에 있는지, (2) 금지 표현이 없는지, (3) 문장에 나온 지표 이름이 근거의 이름인지,
 * (4) 따옴표로 인용한 구절이 서버가 넘긴 근거 구절에 있는지(강점 근거 문장은 그 문장이 근거로 든 구절에서만),
 * (5) 문장에 나온 학년이 근거의 학년인지, (6) 근거로 든 구절이 그 강점 지표와 연결돼 있는지다.
 * 문장 내용 자체는 믿지 않는다. 숫자는 "2학년", "2·3학년" 꼴의 학년만 허용한다.
 */
public final class HolisticNarrativeValidator {

	static final int MAX_SUMMARY_LENGTH = 120;
	static final int MAX_OVERVIEW_LENGTH = 200;
	static final int MAX_EVIDENCE_LENGTH = 240;
	static final int MAX_ADVICE_LENGTH = 160;
	static final int MAX_EVIDENCE_LINES = 3;
	/** 보완할 점 문장 수 상한 */
	static final int MAX_IMPROVEMENT_LINES = 3;
	/** 다음 단계 문장 수 상한. 마지막 한 칸은 서버 고정 문장이 쓴다. */
	static final int MAX_NEXT_STEP_LINES = 2;
	static final int MAX_DETAIL_LINE_LENGTH = 200;
	/** 한 문장이 근거로 삼을 수 있는 구절 수 */
	static final int MAX_EVIDENCE_IDS_PER_LINE = 3;
	/** 따옴표 인용의 최소 길이. 한두 글자는 어느 구절에나 들어 있어 인용 검증이 의미가 없다. */
	static final int MIN_QUOTE_LENGTH = 6;

	private static final Pattern FORBIDDEN = Pattern.compile(
			"\\d|https?:|www\\.|@|%|합격|불합격|보장|확실|반드시|무조건|확률|점수|등급|입결|경쟁률|대학|학과|전형|"
					+ "우울|장애|진단|성격|"
					+ "부족|없어요|없다|없는|없음|못해|못하|미흡|약해|약하|낮아|낮은|뒤처|떨어지");

	/** 줄바꿈과 목록 기호. 태그 이름에 쓰는 가운뎃점(·)은 허용한다. */
	private static final Pattern LINE_BREAK = Pattern.compile("[\\r\\n•\\-*#]");

	/** 허용하는 숫자 표현. 앞의 숫자가 붙어 있으면(예: 13학년) 남는 숫자가 금지 표현에 걸린다. */
	private static final Pattern GRADE = Pattern.compile("[1-3](?:·[1-3])*학년");

	private static final Pattern QUOTE = Pattern.compile("[「『“\"'‘]([^」』”\"'’]*)[」』”\"'’]");

	/** checkLine 이 거절할 때 남기는 원인 힌트. 같은 스레드에서 바로 읽어 가므로 ThreadLocal 로 둔다. */
	private static final ThreadLocal<String> HINT = new ThreadLocal<>();

	private HolisticNarrativeValidator() {
	}

	/** 방금 거절한 글의 힌트를 "코드:힌트" 로 diagnostics 에 옮긴다. */
	private static void note(List<String> diagnostics, String code) {
		String hint = HINT.get();
		HINT.remove();
		if (hint != null) {
			diagnostics.add(code + ":" + hint);
		}
	}

	private static String tail(String text) {
		String stripped = text == null ? "" : text.strip();
		return stripped.length() <= 2 ? stripped : stripped.substring(stripped.length() - 2);
	}

	/**
	 * 검증을 통과한 글.
	 *
	 * @param summary          통과한 활동 요약. 없거나 통과하지 못했으면 null
	 * @param overview         통과한 자세히 보기 전체 요약. 없거나 통과하지 못했으면 null
	 * @param strengthEvidence 통과한 강점 근거 문장(최대 3개). 하나도 없으면 빈 목록
	 * @param advices          통과한 학과별 조언(학과 코드 → 글)
	 * @param rejections       버린 글의 사유 코드. 로그에는 이 고정 코드만 남긴다.
	 * @param improvements     통과한 보완할 점(근거 구절을 든 문장만, 최대 3개)
	 * @param nextSteps        통과한 다음 단계(근거 구절을 든 문장만, 최대 2개)
	 * @param diagnostics      거절 원인을 좁히는 힌트(예: "SUMMARY_STYLE:n=2,end=다."). 글 내용은 담지 않고
	 *                         문장 수·끝 두 글자·걸린 금지어·모르는 근거 이름 정도만 남긴다.
	 */
	public record Checked(Line summary, Line overview, List<Line> strengthEvidence, Map<String, Line> advices,
			List<String> rejections, List<Line> improvements, List<Line> nextSteps, List<String> diagnostics) {

		public Checked {
			diagnostics = List.copyOf(diagnostics);
			strengthEvidence = List.copyOf(strengthEvidence);
			improvements = List.copyOf(improvements);
			nextSteps = List.copyOf(nextSteps);
		}

		/** 보완할 점·다음 단계 없이 만든다(기존 호출 호환). */
		public Checked(Line summary, Line overview, List<Line> strengthEvidence, Map<String, Line> advices,
				List<String> rejections) {
			this(summary, overview, strengthEvidence, advices, rejections, List.of(), List.of(), List.of());
		}
	}

	/** 한 지표가 관찰된 학년(오름차순). 관찰되지 않았으면 빈 목록. */
	static List<Integer> observedYears(HolisticNarrativeFacts facts, String indicatorId) {
		TreeSet<Integer> years = new TreeSet<>();
		for (ElementView element : facts.report().elements()) {
			for (IndicatorView indicator : element.indicators()) {
				if (indicator.id().equals(indicatorId)) {
					years.addAll(indicator.observedYears());
				}
			}
		}
		return List.copyOf(years);
	}

	public static Checked check(HolisticNarrativeFacts facts, ParsedNarrative parsed) {
		Catalog catalog = new Catalog(facts);
		List<String> rejections = new ArrayList<>();
		List<String> diagnostics = new ArrayList<>();
		Set<Integer> knownYears = knownYears(facts);

		Line summary = null;
		Line overview = null;
		if (facts.canSummarize()) {
			String problem = checkLine(parsed.summary(), catalog, facts, null, MAX_SUMMARY_LENGTH, 1, knownYears, facts.evidenceTexts());
			if (problem == null) {
				summary = parsed.summary();
			} else {
				rejections.add("SUMMARY_" + problem);
				note(diagnostics, "SUMMARY_" + problem);
			}
			problem = checkLine(parsed.overview(), catalog, facts, null, MAX_OVERVIEW_LENGTH, 2, knownYears, facts.evidenceTexts());
			if (problem == null) {
				overview = parsed.overview();
			} else {
				rejections.add("OVERVIEW_" + problem);
				note(diagnostics, "OVERVIEW_" + problem);
			}
		}

		List<Line> strengthEvidence = new ArrayList<>();
		if (!facts.evidence().isEmpty()) {
			Set<String> texts = new HashSet<>();
			for (EvidenceLine item : parsed.strengthEvidence()) {
				String problem = checkStrengthEvidence(item, catalog, facts);
				if (problem == null && !texts.add(item.line().text().strip())) {
					problem = "DUPLICATE";
				}
				if (problem != null) {
					rejections.add("EVIDENCE_" + problem);
					note(diagnostics, "EVIDENCE_" + problem);
				} else if (strengthEvidence.size() < MAX_EVIDENCE_LINES) {
					strengthEvidence.add(item.line());
				}
			}
		}

		List<Line> improvements = checkGrounded(parsed.improvements(), "IMPROVEMENT", MAX_IMPROVEMENT_LINES,
				catalog, facts, rejections, diagnostics);
		List<Line> nextSteps = checkGrounded(parsed.nextSteps(), "NEXT_STEP", MAX_NEXT_STEP_LINES,
				catalog, facts, rejections, diagnostics);

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
			String problem = checkLine(item.line(), catalog, facts, major, MAX_ADVICE_LENGTH, 2, knownYears, facts.evidenceTexts());
			if (problem == null) {
				advices.put(item.majorCode(), item.line());
			} else {
				rejections.add("ADVICE_" + problem);
				note(diagnostics, "ADVICE_" + problem);
			}
		}
		return new Checked(summary, overview, strengthEvidence, advices, rejections, improvements, nextSteps, diagnostics);
	}

	/**
	 * 보완할 점·다음 단계. 근거 구절이 있을 때만 받고, 문장마다 근거로 든 구절 번호가 입력에 있어야 한다.
	 * 강점 근거와 달리 강점 지표만 가리킬 필요는 없다(관찰된 지표·요소·관심 태그를 refs 로 쓸 수 있다).
	 * 구절에 지표 연결이 있고 문장이 지표를 가리켰다면 그 구절은 그 지표 중 하나의 근거여야 한다.
	 */
	private static List<Line> checkGrounded(List<EvidenceLine> items, String code, int max, Catalog catalog,
			HolisticNarrativeFacts facts, List<String> rejections, List<String> diagnostics) {
		List<Line> accepted = new ArrayList<>();
		if (facts.evidence().isEmpty()) {
			return accepted;
		}
		Map<String, HolisticNarrativeFacts.EvidencePhrase> known = new HashMap<>();
		facts.evidence().forEach(phrase -> known.put(phrase.id(), phrase));
		Set<String> texts = new HashSet<>();
		for (EvidenceLine item : items) {
			String problem = checkGroundedLine(item, catalog, facts, known);
			if (problem == null && !texts.add(item.line().text().strip())) {
				problem = "DUPLICATE";
			}
			if (problem != null) {
				rejections.add(code + "_" + problem);
				note(diagnostics, code + "_" + problem);
			} else if (accepted.size() < max) {
				accepted.add(item.line());
			}
		}
		return accepted;
	}

	private static String checkGroundedLine(EvidenceLine item, Catalog catalog, HolisticNarrativeFacts facts,
			Map<String, HolisticNarrativeFacts.EvidencePhrase> known) {
		if (item.evidenceIds().isEmpty()) {
			return "NO_EVIDENCE_IDS";
		}
		if (item.evidenceIds().size() > MAX_EVIDENCE_IDS_PER_LINE) {
			return "TOO_MANY_EVIDENCE_IDS";
		}
		Set<String> indicatorRefs = new HashSet<>();
		for (String ref : item.line().refs()) {
			if (catalog.indicatorIds.contains(ref)) {
				indicatorRefs.add(ref);
			}
		}
		Set<Integer> years = new TreeSet<>();
		List<String> cited = new ArrayList<>();
		for (String id : item.evidenceIds()) {
			HolisticNarrativeFacts.EvidencePhrase phrase = known.get(id);
			if (phrase == null) {
				return "UNKNOWN_EVIDENCE_ID";
			}
			if (!indicatorRefs.isEmpty() && !phrase.indicators().isEmpty()
					&& phrase.indicators().stream().noneMatch(indicatorRefs::contains)) {
				return "NOT_LINKED";
			}
			if (phrase.year() != null) {
				years.add(phrase.year());
			}
			cited.add(phrase.text());
		}
		return checkLine(item.line(), catalog, facts, null, MAX_DETAIL_LINE_LENGTH, 2, years, cited);
	}

	/** 글에 학년이 나와도 되는 범위. 어느 지표든 관찰된 학년과 근거 구절의 학년이다. */
	private static Set<Integer> knownYears(HolisticNarrativeFacts facts) {
		Set<Integer> years = new TreeSet<>();
		facts.report().elements().forEach(element ->
				element.indicators().forEach(indicator -> years.addAll(indicator.observedYears())));
		facts.evidence().stream().map(HolisticNarrativeFacts.EvidencePhrase::year)
				.filter(year -> year != null).forEach(years::add);
		return years;
	}

	/** 강점 근거 문장은 강점 지표와 근거 구절을 가리켜야 하고, 학년은 그 근거의 학년만 쓸 수 있다. */
	private static String checkStrengthEvidence(EvidenceLine item, Catalog catalog, HolisticNarrativeFacts facts) {
		Set<String> strengthIds = new HashSet<>();
		facts.strengths().forEach(strength -> strengthIds.add(strength.indicatorId()));
		Set<Integer> years = new TreeSet<>();
		for (String ref : item.line().refs()) {
			if (!strengthIds.contains(ref)) {
				return "REF_NOT_STRENGTH";
			}
			years.addAll(observedYears(facts, ref));
		}
		if (item.evidenceIds().isEmpty()) {
			return "NO_EVIDENCE_IDS";
		}
		if (item.evidenceIds().size() > MAX_EVIDENCE_IDS_PER_LINE) {
			return "TOO_MANY_EVIDENCE_IDS";
		}
		Map<String, HolisticNarrativeFacts.EvidencePhrase> known = new HashMap<>();
		facts.evidence().forEach(phrase -> known.put(phrase.id(), phrase));
		List<String> cited = new ArrayList<>();
		for (String id : item.evidenceIds()) {
			HolisticNarrativeFacts.EvidencePhrase phrase = known.get(id);
			if (phrase == null) {
				return "UNKNOWN_EVIDENCE_ID";
			}
			// 구절에 지표 연결이 있으면 그 구절은 이 문장이 말하는 강점 지표 중 하나의 근거여야 한다.
			if (!phrase.indicators().isEmpty()
					&& phrase.indicators().stream().noneMatch(item.line().refs()::contains)) {
				return "NOT_LINKED";
			}
			if (phrase.year() != null) {
				years.add(phrase.year());
			}
			cited.add(phrase.text());
		}
		return checkLine(item.line(), catalog, facts, null, MAX_EVIDENCE_LENGTH, 2, years, cited);
	}

	private static String checkLine(Line line, Catalog catalog, HolisticNarrativeFacts facts, MajorFacts major,
			int maxLength, int maxSentences, Collection<Integer> allowedYears, List<String> quoteSources) {
		String text = line.text() == null ? "" : line.text().strip();
		if (text.isEmpty() || text.length() > maxLength) {
			return "TEXT_LENGTH";
		}
		if (LINE_BREAK.matcher(text).find()) {
			return "FORMAT";
		}
		// 「」 안은 학생 기록의 원문이라 금지어 검사에서 뺀다(예: 「대학 공학 체험」). 원문 일치는 checkQuotes 가 따로 본다.
		Matcher forbidden = FORBIDDEN.matcher(GRADE.matcher(QUOTE.matcher(text).replaceAll("")).replaceAll(""));
		if (forbidden.find()) {
			HINT.set("word=" + forbidden.group());
			return "FORBIDDEN";
		}
		// 「」 안은 학생 기록 원문이라 "다."로 끝나는 문장이 들어 있을 수 있다. 문체 검사는 인용을 뺀 나머지 글에만 한다.
		String styled = QUOTE.matcher(text).replaceAll("인용");
		if (!isHaeyo(styled, maxSentences)) {
			HINT.set("n=" + styled.split("\\.").length + ",max=" + maxSentences + ",end=" + tail(styled));
			return "STYLE";
		}
		if (!gradesWithin(text, allowedYears)) {
			return "GRADE";
		}
		String quote = checkQuotes(text, quoteSources);
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
				HINT.set("ref=" + (ref == null ? "null" : ref.substring(0, Math.min(12, ref.length()))));
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

	/** 글에 나온 학년이 모두 허용한 학년 안에 있는가. */
	private static boolean gradesWithin(String text, Collection<Integer> allowed) {
		Matcher matcher = GRADE.matcher(text);
		while (matcher.find()) {
			for (char c : matcher.group().toCharArray()) {
				if (Character.isDigit(c) && !allowed.contains(c - '0')) {
					return false;
				}
			}
		}
		return true;
	}

	/** 모든 문장이 "요."로 끝나는 해요체이고 문장 수가 한도 이내여야 한다. */
	private static boolean isHaeyo(String text, int maxSentences) {
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
		return count >= 1 && count <= maxSentences;
	}

	/** 따옴표로 인용한 구절은 허용된 근거 구절 안에 있고 최소 길이 이상이어야 한다. 근거 구절이 없으면 인용 자체를 받지 않는다. */
	private static String checkQuotes(String text, List<String> evidence) {
		Matcher matcher = QUOTE.matcher(text);
		while (matcher.find()) {
			String quoted = matcher.group(1).strip();
			boolean tooShort = quoted.length() < MIN_QUOTE_LENGTH;
			if (tooShort || evidence.stream().noneMatch(phrase -> phrase.contains(quoted))) {
				HINT.set(tooShort ? "short" : "not_in_evidence,len=" + quoted.length());
				return "QUOTE";
			}
		}
		return null;
	}

	/** 근거로 쓸 수 있는 값과 그 값이 허용하는 이름들 */
	private static final class Catalog {
		final Map<String, Set<String>> refLabels = new HashMap<>();
		final Set<String> allLabels = new LinkedHashSet<>();
		/** 관찰된 지표 id */
		final Set<String> indicatorIds = new HashSet<>();

		Catalog(HolisticNarrativeFacts facts) {
			for (ElementView element : facts.report().elements()) {
				Set<String> elementLabels = new LinkedHashSet<>();
				elementLabels.add(element.label());
				allLabels.add(element.label());
				for (IndicatorView indicator : element.indicators()) {
					elementLabels.add(indicator.label());
					allLabels.add(indicator.label());
					refLabels.put(indicator.id(), Set.of(indicator.label()));
					indicatorIds.add(indicator.id());
				}
				// 관찰되지 않은 지표는 이름만 보호하고 근거로는 받지 않는다.
				for (NotObserved indicator : element.notObserved()) {
					allLabels.add(indicator.label());
				}
				refLabels.put(element.id(), elementLabels);
			}
			facts.report().notMeasured().forEach(item -> allLabels.remove(item.label()));
			facts.report().interestTags().forEach(tag -> refLabels.putIfAbsent(tag, Set.of()));
			// 프롬프트가 다음 단계의 refs에 희망 학과 태그를 쓰라고 하므로, 학과 태그도 근거로 받는다.
			facts.majors().forEach(major -> major.tags().forEach(tag -> refLabels.putIfAbsent(tag, Set.of())));
		}
	}
}
