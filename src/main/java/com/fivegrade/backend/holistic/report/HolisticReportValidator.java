package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticReportInput.ElementView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.IndicatorView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotMeasured;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotObserved;
import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI가 쓴 리포트 초안을 서버가 확정한 입력 자료와 대조한다. 순수 Java 클래스다.
 * 하나라도 어긋나면 초안 전체를 버리고 기본 문장(템플릿)을 쓴다. 일부만 고쳐 쓰지 않는다.
 *
 * 문장 내용 자체는 믿지 않는다. 믿는 것은 (1) 근거 id가 입력에 있는지, (2) id가 가리키는 구간이 문장 종류와 맞는지,
 * (3) 금지 표현이 없는지, (4) 문장에 나온 지표 이름이 근거 id의 이름인지다.
 */
public final class HolisticReportValidator {

	static final int MAX_TEXT_LENGTH = 120;

	private static final Pattern FORBIDDEN = Pattern.compile(
			"\\d|https?:|www\\.|@|%|합격|불합격|보장|확실|반드시|무조건|확률|점수|등급|입결|경쟁률|대학|학과|전형|"
					+ "우울|장애|진단|성격|"
					+ "부족|없어요|없다|없는|없음|못해|못하|미흡|약해|약하|낮아|낮은|뒤처|떨어지");

	private static final Pattern NOT_HAERYO = Pattern.compile("(다|까|라)\\s*[.!?]?\\s*$");
	private static final Pattern LINE_BREAK = Pattern.compile("[\\r\\n•·\\-*#]");

	private HolisticReportValidator() {
	}

	/** 통과하면 true. 어긋나면 false. 이유는 남기지 않는다(내용을 로그에 쓰지 않기 위해). */
	public static boolean isValid(HolisticReportInput input, ParsedReport report) {
		return rejection(input, report) == null;
	}

	/** 거절 사유 코드. 통과하면 null. 로그에는 이 고정 코드만 남긴다. */
	public static String rejection(HolisticReportInput input, ParsedReport report) {
		if (report.strengths().size() > HolisticReportTemplate.MAX_STRENGTHS
				|| report.toDevelop().size() > HolisticReportTemplate.MAX_TO_DEVELOP
				|| report.nextSteps().size() > HolisticReportTemplate.MAX_NEXT_STEPS) {
			return "COUNT";
		}

		Catalog catalog = new Catalog(input);
		Set<String> seen = new HashSet<>();

		String problem = checkLine(report.summary(), catalog, Kind.SUMMARY, seen);
		if (problem != null) {
			return problem;
		}
		for (Line line : report.strengths()) {
			problem = checkLine(line, catalog, Kind.STRENGTH, seen);
			if (problem != null) {
				return problem;
			}
		}
		for (Line line : report.toDevelop()) {
			problem = checkLine(line, catalog, Kind.TO_DEVELOP, seen);
			if (problem != null) {
				return problem;
			}
		}
		for (Line line : report.nextSteps()) {
			problem = checkLine(line, catalog, Kind.NEXT_STEP, seen);
			if (problem != null) {
				return problem;
			}
		}
		return null;
	}

	private enum Kind {
		SUMMARY, STRENGTH, TO_DEVELOP, NEXT_STEP
	}

	private static String checkLine(Line line, Catalog catalog, Kind kind, Set<String> seen) {
		String text = line.text() == null ? "" : line.text().strip();
		if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
			return "TEXT_LENGTH";
		}
		if (LINE_BREAK.matcher(text).find()) {
			return "FORMAT";
		}
		if (FORBIDDEN.matcher(text).find()) {
			return "FORBIDDEN";
		}
		if (NOT_HAERYO.matcher(text).find() || !text.endsWith("요.")) {
			return "STYLE";
		}
		if (!seen.add(text)) {
			return "DUPLICATE";
		}
		if (line.refs().isEmpty()) {
			return "NO_REFS";
		}

		Set<String> allowedLabels = new LinkedHashSet<>();
		boolean allMatchKind = true;
		for (String ref : line.refs()) {
			Ref known = catalog.refs.get(ref);
			if (known == null) {
				return "UNKNOWN_REF";
			}
			allowedLabels.addAll(known.labels());
			if (!matches(kind, known, catalog)) {
				allMatchKind = false;
			}
		}
		if (!allMatchKind) {
			return "REF_BAND";
		}

		for (NotMeasured notMeasured : catalog.notMeasured) {
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

	private static boolean matches(Kind kind, Ref ref, Catalog catalog) {
		return switch (kind) {
			case SUMMARY -> true;
			case STRENGTH -> ref.band == ReportBand.HIGH;
			case TO_DEVELOP -> ref.weak;
			// 보완할 항목이 하나도 없으면 일반 제안이므로 어떤 근거든 허용한다.
			case NEXT_STEP -> ref.weak || !catalog.hasWeak;
		};
	}

	/** 근거로 쓸 수 있는 id 한 개의 정보 */
	private record Ref(ReportBand band, boolean weak, Set<String> labels) {
	}

	private static final class Catalog {
		final Map<String, Ref> refs = new HashMap<>();
		final Set<String> allLabels = new LinkedHashSet<>();
		final List<NotMeasured> notMeasured;
		boolean hasWeak;

		Catalog(HolisticReportInput input) {
			this.notMeasured = input.notMeasured();
			for (ElementView element : input.elements()) {
				Set<String> elementLabels = new LinkedHashSet<>();
				elementLabels.add(element.label());
				allLabels.add(element.label());
				for (IndicatorView indicator : element.indicators()) {
					elementLabels.add(indicator.label());
					allLabels.add(indicator.label());
					refs.put(indicator.id(), new Ref(indicator.band(),
							indicator.band() == ReportBand.LOW, Set.of(indicator.label())));
				}
				for (NotObserved indicator : element.notObserved()) {
					elementLabels.add(indicator.label());
					allLabels.add(indicator.label());
					// band 가 없는 지표는 '보완 후보'로만 쓰고 강점 근거로는 쓰지 못한다.
					refs.put(indicator.id(), new Ref(null, true, Set.of(indicator.label())));
				}
				boolean weakElement = element.band() == ReportBand.LOW
						|| !element.notObserved().isEmpty();
				refs.put(element.id(), new Ref(element.band(), weakElement, elementLabels));
			}
			hasWeak = refs.values().stream().anyMatch(Ref::weak);
			// 측정하지 않는 지표의 id 는 근거로 받지 않는다(refs 에 넣지 않음).
			new ArrayList<>(notMeasured).forEach(item -> allLabels.remove(item.label()));
		}
	}
}
