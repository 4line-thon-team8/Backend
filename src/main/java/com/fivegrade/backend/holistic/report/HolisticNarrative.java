package com.fivegrade.backend.holistic.report;

import java.util.List;
import java.util.Objects;

/**
 * 활동 요약, 학과별 조언, 자세히 보기(detail). 글마다 생성 방식(TEMPLATE/LLM)이 따로 붙는다.
 *
 * @param activitySummary 활동 요약 한 문장. 근거가 부족하면 null
 * @param majorAdvices    희망 학과별 조언(희망 학과 입력 순서)
 * @param detail          강점 카드 '자세히 보기'에 쓰는 글. 활동 요약 근거가 부족하면 null
 */
public record HolisticNarrative(Text activitySummary, List<MajorAdvice> majorAdvices, Detail detail) {

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

	/**
	 * @param overview         활동 흐름 요약(1~2문장). 없으면 null
	 * @param strengthEvidence 강점을 뒷받침하는 근거 문장(최대 3개)
	 * @param improvements     보완하면 좋은 점(최대 3개)
	 * @param nextSteps        다음에 준비할 것(최대 3개)
	 */
	public record Detail(Text overview, List<Text> strengthEvidence, List<Text> improvements, List<Text> nextSteps) {
		public Detail {
			strengthEvidence = List.copyOf(strengthEvidence);
			improvements = List.copyOf(improvements);
			nextSteps = List.copyOf(nextSteps);
		}
	}

	public HolisticNarrative {
		majorAdvices = List.copyOf(majorAdvices);
	}

	public static HolisticNarrative empty() {
		return new HolisticNarrative(null, List.of(), null);
	}
}
