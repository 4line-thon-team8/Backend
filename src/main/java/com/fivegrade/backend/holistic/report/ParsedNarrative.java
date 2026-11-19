package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import java.util.List;
import java.util.Objects;

/** AI가 돌려준 활동 요약·자세히 보기·학과별 조언 초안. 검증을 통과하기 전의 값이라 신뢰하지 않는다. */
public record ParsedNarrative(
		Line summary,
		Line overview,
		List<EvidenceLine> strengthEvidence,
		List<MajorLine> majorAdvices,
		List<EvidenceLine> improvements,
		List<EvidenceLine> nextSteps
) {

	/** 근거 구절을 가리키는 문장 하나(강점 근거·보완할 점·다음 단계). evidenceIds 는 이 문장이 근거로 삼은 근거 구절 번호다. */
	public record EvidenceLine(Line line, List<String> evidenceIds) {
		public EvidenceLine {
			Objects.requireNonNull(line);
			evidenceIds = List.copyOf(evidenceIds);
		}
	}

	/** 학과 코드 하나에 대한 조언. */
	public record MajorLine(String majorCode, Line line) {
		public MajorLine {
			Objects.requireNonNull(majorCode);
			Objects.requireNonNull(line);
		}
	}

	public ParsedNarrative {
		Objects.requireNonNull(summary);
		Objects.requireNonNull(overview);
		strengthEvidence = List.copyOf(strengthEvidence);
		majorAdvices = List.copyOf(majorAdvices);
		improvements = List.copyOf(improvements);
		nextSteps = List.copyOf(nextSteps);
	}

	/** 보완할 점·다음 단계 없이 만든다(기존 호출 호환). */
	public ParsedNarrative(Line summary, Line overview, List<EvidenceLine> strengthEvidence,
			List<MajorLine> majorAdvices) {
		this(summary, overview, strengthEvidence, majorAdvices, List.of(), List.of());
	}
}
