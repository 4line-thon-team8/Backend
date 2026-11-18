package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import java.util.List;
import java.util.Objects;

/** AI가 돌려준 활동 요약·학과별 조언 초안. 검증을 통과하기 전의 값이라 신뢰하지 않는다. */
public record ParsedNarrative(Line summary, List<MajorLine> majorAdvices) {

	/** 학과 코드 하나에 대한 조언. */
	public record MajorLine(String majorCode, Line line) {
		public MajorLine {
			Objects.requireNonNull(majorCode);
			Objects.requireNonNull(line);
		}
	}

	public ParsedNarrative {
		Objects.requireNonNull(summary);
		majorAdvices = List.copyOf(majorAdvices);
	}
}
