package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * POST /api/holistic-reports 응답. 화면이 요소·지표를 그릴 수 있는 구조화 값과, 글로 된 리포트를 함께 돌려준다.
 * 글에는 숫자를 쓰지 않으므로 수치 표현은 band(구간)나 level(1~5 정수)로 그린다.
 *
 * @param status       APPLIED=리포트를 만들었다, SKIPPED=서술 분석을 반영하지 못해 만들지 못했다
 * @param skipReason   SKIPPED 일 때만 값이 있다(DISABLED, RULES_OUTDATED, TOO_SHORT, INSUFFICIENT_SIGNALS)
 * @param rulesVersion APPLIED 일 때 분석에 쓴 규칙 버전
 * @param elements     APPLIED 일 때 평가요소 4개
 * @param notMeasured  APPLIED 일 때 규칙이 없어 측정하지 않는 지표
 * @param report       APPLIED 일 때 글 리포트
 * @param disclaimer   APPLIED 일 때 참고용 안내
 */
public record HolisticReportResponse(
		@Schema(allowableValues = {"APPLIED", "SKIPPED"})
		String status,
		@Schema(allowableValues = {"DISABLED", "RULES_OUTDATED", "TOO_SHORT", "INSUFFICIENT_SIGNALS"}, nullable = true)
		String skipReason,
		@Schema(nullable = true)
		String rulesVersion,
		@Schema(nullable = true)
		List<ElementItem> elements,
		@Schema(nullable = true)
		List<NotMeasuredItem> notMeasured,
		@Schema(nullable = true)
		ReportItem report,
		@Schema(nullable = true)
		String disclaimer
) {

	static final String DISCLAIMER =
			"이 리포트는 생기부 서술 분석 결과를 바탕으로 한 참고용이에요. 입시 결과를 보장하지 않아요.";

	/** @param band 판단하지 못한 요소는 null. HIGH/MID/LOW
	 *  @param level 판단하지 못한 요소는 null. 화면 막대용 1~5 정수 */
	public record ElementItem(
			@Schema(allowableValues = {"ACADEMIC", "MAJOR_FIT", "COMMUNITY", "POTENTIAL"})
			String id,
			String label,
			@Schema(allowableValues = {"HIGH", "MID", "LOW"}, nullable = true)
			String band,
			@Schema(nullable = true)
			Integer level,
			List<IndicatorItem> indicators,
			@Schema(description = "관찰할 수 있는 지표인데 이번 분석에서 신호가 잡히지 않은 지표. 없다는 뜻이 아니다.")
			List<NotObservedItem> notObserved
	) {
	}

	/** @param reliability 이 지표 판별 모델의 신뢰도. LOW 인 지표는 단정해서 보여 주지 않는 것이 좋다. */
	public record IndicatorItem(
			String id,
			String label,
			@Schema(allowableValues = {"HIGH", "MID", "LOW"})
			String band,
			int level,
			@Schema(description = "신호가 나타난 학년(1~3)")
			List<Integer> observedYears,
			@Schema(allowableValues = {"HIGH", "MID", "LOW"})
			String reliability
	) {
	}

	public record NotObservedItem(String id, String label) {
	}

	public record NotMeasuredItem(String id, String label) {
	}

	/** @param generationMethod template 또는 llm */
	public record TextItem(String text, String generationMethod) {
	}

	public record ReportItem(
			TextItem summary,
			List<TextItem> strengths,
			List<TextItem> toDevelop,
			List<TextItem> nextSteps,
			TextItem caveat
	) {
	}

	public static HolisticReportResponse skipped(HolisticEvaluation.SkipReason reason) {
		return new HolisticReportResponse("SKIPPED", reason.name(), null, null, null, null, null);
	}
}
