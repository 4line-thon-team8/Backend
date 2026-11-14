package com.fivegrade.backend.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * POST /api/simulations 공개 응답(초안).
 *
 * 지금까지 합의한 필드만 담는다. conversion, saveStatus, disclaimer 등 미확정 필드는
 * 공개 응답 계약(#61)이 확정된 뒤에 추가한다. 내부 결과(SimulationReportResult)를 그대로 내보내지 않고
 * SimulationResponseMapper 에서 이 모양으로 바꾼다. 필드 이름을 바꿀 때는 매퍼만 고치면 된다.
 *
 * 값이 없는 필드(gradeGap, evidence 일부, warnings.rank)는 키를 빼지 않고 null로 내려간다.
 *
 * @param displayAverage  화면 표시용 참고 평균(소수 둘째 자리)
 * @param subjects        요청 순서와 subjectRef를 유지한 과목별 입력 등급
 * @param recommendations 추천 원서 0~6장. rank 순서
 * @param warnings        안내 코드. 없으면 빈 배열. 유사 학과·자율전공으로 넓힌 안내는 결과 전체 안내(rank null)로 한 번만
 * 내려가고, 선호 전형이 아닌 전형으로 넓힌 안내(OTHER_TRACK_USED)는 내려가지 않는다. 카드의 넓힌 경로는 candidateKind 로 알 수 있다.
 * @param report          컨설팅 리포트
 */
public record SimulationResponse(
		BigDecimal displayAverage,
		List<SubjectResult> subjects,
		List<RecommendationItem> recommendations,
		List<WarningItem> warnings,
		ReportBody report
) {

	/** 요청 과목 행 하나. 사용자가 입력한 5등급 값을 그대로 돌려준다. */
	public record SubjectResult(String subjectRef, String subjectCode, BigDecimal grade) {
	}

	/**
	 * 추천 원서 한 장.
	 *
	 * @param category      합격 가능성 구간(SAFE, MATCH, REACH, FAR, OVER, UNRATED)
	 * @param candidateKind 후보가 오른 경로(DESIRED_MAJOR, SIMILAR_MAJOR, OTHER_TRACK, UNDECLARED)
	 * @param gradeGap      예측 70%컷(5등급 환산)에서 사용자 평균을 뺀 값. 소수 둘째 자리. UNRATED면 null
	 */
	public record RecommendationItem(
			int rank,
			String university,
			String department,
			String admissionType,
			String category,
			String candidateKind,
			BigDecimal gradeGap,
			Evidence evidence,
			Explanation reason
	) {
	}

	/**
	 * 추천 근거. 적재되지 않은 값은 null이다.
	 *
	 * 학과 신설·통합 여부는 FE와 합의한 이름(isNewOrMergedDepartment)으로 내려간다.
	 * 이름이 is 로 시작하는 Boolean 컴포넌트가 JSON 키에서 is 가 빠지지 않도록 키를 직접 지정한다.
	 */
	public record Evidence(
			Integer quota,
			Double competitionRate,
			@JsonProperty("isNewOrMergedDepartment") Boolean isNewOrMergedDepartment,
			int basisYears
	) {
	}

	/** 설명 문장과 생성 방식(template 또는 llm). */
	public record Explanation(String text, String generationMethod) {
	}

	/**
	 * 안내 한 건. 화면 분기는 code로 하고 message는 문구만 바뀔 수 있는 값이다.
	 *
	 * @param rank 해당 카드의 지망 순서. 결과 전체에 해당하면 null
	 */
	public record WarningItem(String code, Integer rank, String message) {
	}

	public record ReportBody(
			Explanation greetingSummary,
			Explanation overallAssessment,
			Explanation applicationStrategy,
			Explanation closing
	) {
	}

}
