package com.fivegrade.backend.holistic.report;

import java.util.List;
import java.util.Objects;

/**
 * AI가 돌려준 리포트 초안. 검증을 통과하기 전의 값이라 신뢰하지 않는다.
 * 문장마다 근거가 되는 요소·지표 ID(refs)를 달게 해서 서버가 입력 자료와 대조한다.
 */
public record ParsedReport(
		Line summary,
		List<Line> strengths,
		List<Line> toDevelop,
		List<Line> nextSteps
) {

	public record Line(String text, List<String> refs) {
		public Line {
			Objects.requireNonNull(text);
			refs = List.copyOf(refs);
		}
	}

	public ParsedReport {
		Objects.requireNonNull(summary);
		strengths = List.copyOf(strengths);
		toDevelop = List.copyOf(toDevelop);
		nextSteps = List.copyOf(nextSteps);
	}
}
