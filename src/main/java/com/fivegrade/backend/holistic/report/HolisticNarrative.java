package com.fivegrade.backend.holistic.report;

import java.util.List;
import java.util.Objects;

/**
 * 활동 요약과 학과별 조언. 글마다 생성 방식(TEMPLATE/LLM)이 따로 붙는다.
 *
 * @param activitySummary 활동 요약. 근거가 부족하면 null
 * @param majorAdvices    희망 학과별 조언(희망 학과 입력 순서)
 */
public record HolisticNarrative(Text activitySummary, List<MajorAdvice> majorAdvices) {

	public static final String TEMPLATE = "TEMPLATE";
	public static final String LLM = "LLM";

	public record Text(String text, String generationMethod) {
		public Text {
			if (text == null || text.isBlank()) {
				throw new IllegalArgumentException("narrative text is required");
			}
			if (!TEMPLATE.equals(generationMethod) && !LLM.equals(generationMethod)) {
				throw new IllegalArgumentException("unsupported generation method");
			}
		}

		public static Text template(String text) {
			return new Text(text, TEMPLATE);
		}

		public static Text llm(String text) {
			return new Text(text, LLM);
		}
	}

	public record MajorAdvice(String majorCode, String department, Text advice) {
		public MajorAdvice {
			Objects.requireNonNull(majorCode);
			Objects.requireNonNull(department);
			Objects.requireNonNull(advice);
		}
	}

	public HolisticNarrative {
		majorAdvices = List.copyOf(majorAdvices);
	}

	public static HolisticNarrative empty() {
		return new HolisticNarrative(null, List.of());
	}
}
