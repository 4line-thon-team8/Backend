package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticNarrativeFacts.MajorFacts;
import com.fivegrade.backend.holistic.report.HolisticStrengthSelector.Strength;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 서버가 가진 값으로 {@link HolisticNarrativeFacts}를 만든다. 순수 Java 클래스다. */
public final class HolisticNarrativeFactsFactory {

	static final int MAX_MAJORS = 3;
	static final int MAX_EVIDENCE = 30;
	static final int MAX_EVIDENCE_TEXT = 30;
	/** 인용 구절(완결 문장)의 길이 상한. 지표 연결이 있는 구절만 이 상한을 쓴다. */
	static final int MAX_CITATION_TEXT = 100;

	private HolisticNarrativeFactsFactory() {
	}

	/** 희망 학과 하나. */
	public record DesiredMajor(String code, String name) {
	}

	/**
	 * 요청에서 읽은 근거 구절 하나.
	 *
	 * @param indicators 지표 연결. 비어 있으면 짧은 조각 구절(30자 이하), 있으면 인용 구절(100자 이하)로 본다.
	 */
	public record EvidenceInput(String text, Integer year, String section, List<String> indicators) {

		public EvidenceInput {
			indicators = indicators == null ? List.of() : List.copyOf(indicators);
		}

		public EvidenceInput(String text, Integer year, String section) {
			this(text, year, section, List.of());
		}
	}

	/**
	 * @param majorTags      학과 코드 → 전공 태그(연관 강도가 높은 순). 없는 학과는 빈 목록으로 본다.
	 * @param evidence       근거 구절. 외부 AI 전송 스위치가 꺼져 있으면 빈 목록을 넘긴다.
	 */
	public static HolisticNarrativeFacts create(
			HolisticReportInput report,
			List<Strength> strengths,
			List<DesiredMajor> desired,
			Map<String, List<String>> majorTags,
			List<EvidenceInput> evidence
	) {
		Set<String> interest = new LinkedHashSet<>(report.interestTags());
		List<MajorFacts> majors = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (DesiredMajor major : desired) {
			if (majors.size() >= MAX_MAJORS || !seen.add(major.code())) {
				continue;
			}
			List<String> tags = majorTags.getOrDefault(major.code(), List.of());
			List<String> overlap = tags.stream().filter(interest::contains).toList();
			majors.add(new MajorFacts(major.code(), major.name(), tags, overlap));
		}
		List<HolisticNarrativeFacts.EvidencePhrase> phrases = new ArrayList<>();
		Set<String> texts = new LinkedHashSet<>();
		for (EvidenceInput input : evidence) {
			String text = input.text() == null ? "" : input.text().strip();
			int maxText = input.indicators().isEmpty() ? MAX_EVIDENCE_TEXT : MAX_CITATION_TEXT;
			if (phrases.size() >= MAX_EVIDENCE || text.isEmpty() || text.length() > maxText
					|| !texts.add(text)) {
				continue;
			}
			phrases.add(new HolisticNarrativeFacts.EvidencePhrase(
					"e" + (phrases.size() + 1), text, input.year(), input.section(), input.indicators()));
		}
		return new HolisticNarrativeFacts(report, strengths, majors, phrases);
	}
}
