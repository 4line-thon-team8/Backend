package com.fivegrade.backend.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * POST /api/simulations 공개 응답(초안).
 *
 * 지금까지 합의한 필드만 담는다. conversion, disclaimer 등 미확정 필드는 공개 응답 계약(#61)이 확정된 뒤에 추가한다.
 * 자동 저장 결과(saveStatus, reportId)는 기능명세 S-05 #15(저장 안내 문구)에 필요해서 포함한다. 내부 결과(SimulationReportResult)를 그대로 내보내지 않고
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
 * @param saveStatus      리포트 자동 저장 결과. SAVED(저장됨, 같은 Idempotency-Key 로 이미 저장된 경우 포함),
 * FAILED(저장 실패, 분석 결과는 그대로 내려감), NOT_SAVED(저장 대상 아님, 추천 0장)
 * @param reportId        저장된 리포트 ID. SAVED 일 때만 값이 있고 나머지는 null
 */
public record SimulationResponse(
		@Schema(description = "화면 표시용 참고 평균(소수 둘째 자리). 입력 과목을 같은 비중으로 계산한 값.", example = "2.17")
		BigDecimal displayAverage,
		@Schema(description = "요청 순서와 subjectRef를 유지한 과목별 입력 등급")
		List<SubjectResult> subjects,
		@Schema(description = "추천 원서 0~6장(rank 순서). 후보가 6장에 못 미치면 있는 만큼만, 0장이면 빈 배열.")
		List<RecommendationItem> recommendations,
		@Schema(description = "안내 코드 목록. 없으면 빈 배열. 유사 학과·자율전공 확장 안내는 결과 전체 안내(rank null)로 한 번만 내려간다.")
		List<WarningItem> warnings,
		@Schema(description = "컨설팅 리포트. 인사·요약, 총평, 지원 전략, 마무리 안내.")
		ReportBody report,
		@Schema(description = "리포트 자동 저장 결과. SAVED=저장됨(같은 Idempotency-Key의 기존 저장본 재사용 포함), FAILED=저장 실패(분석 결과는 그대로), NOT_SAVED=저장 대상 아님(추천 0장).", allowableValues = {"SAVED", "FAILED", "NOT_SAVED"})
		String saveStatus,
		@Schema(description = "저장된 리포트 ID. SAVED일 때만 값이 있고 나머지는 null. 저장 리포트 API의 id와 같은 값.", nullable = true)
		UUID reportId,
		@Schema(description = "서술 분석 반영 결과. 서술 분석을 보내지 않았으면 status 가 NOT_PROVIDED. 저장하지 않는 값이다.")
		HolisticResult holistic
) {

	/** 서술 분석 결과가 없는 응답. 서술 분석을 보내지 않은 것(NOT_PROVIDED)으로 내려간다. */
	public SimulationResponse(
			BigDecimal displayAverage,
			List<SubjectResult> subjects,
			List<RecommendationItem> recommendations,
			List<WarningItem> warnings,
			ReportBody report,
			String saveStatus,
			UUID reportId
	) {
		this(displayAverage, subjects, recommendations, warnings, report, saveStatus, reportId,
				HolisticResult.notProvided());
	}

	/**
	 * 서술 분석 반영 결과.
	 *
	 * @param status        APPLIED=반영함, SKIPPED=보냈지만 반영하지 못함, NOT_PROVIDED=보내지 않음
	 * @param skipReason    SKIPPED 일 때만 값이 있다(DISABLED, RULES_OUTDATED, TOO_SHORT, INSUFFICIENT_SIGNALS)
	 * @param rulesVersion  APPLIED 일 때 분석에 쓴 규칙 버전
	 * @param elementLevels APPLIED 일 때 평가요소별 수준(1~5, 소수 둘째 자리). 판단하지 못한 요소는 들어 있지 않다.
	 * @param overallLevel  APPLIED 일 때 전체 서류 수준(1~5, 소수 둘째 자리)
	 */
	public record HolisticResult(
			@Schema(description = "APPLIED=반영함, SKIPPED=보냈지만 반영하지 못함, NOT_PROVIDED=보내지 않음", allowableValues = {"APPLIED", "SKIPPED", "NOT_PROVIDED"})
			String status,
			@Schema(description = "SKIPPED일 때만 값이 있다.", allowableValues = {"DISABLED", "RULES_OUTDATED", "TOO_SHORT", "INSUFFICIENT_SIGNALS"}, nullable = true)
			String skipReason,
			@Schema(description = "APPLIED일 때 분석에 쓴 규칙 버전", nullable = true)
			String rulesVersion,
			@Schema(description = "APPLIED일 때 평가요소(ACADEMIC, MAJOR_FIT, COMMUNITY, POTENTIAL)별 수준 1~5. 판단하지 못한 요소는 없다.", nullable = true)
			java.util.Map<String, BigDecimal> elementLevels,
			@Schema(description = "APPLIED일 때 전체 서류 수준 1~5", nullable = true)
			BigDecimal overallLevel
	) {

		public static HolisticResult notProvided() {
			return new HolisticResult("NOT_PROVIDED", null, null, null, null);
		}
	}

	/** 서류 적합도. 학종 후보에 서류 입력이 있을 때만 카드에 붙는다. */
	public record DocumentFitItem(
			@Schema(description = "서류 적합도 단계", allowableValues = {"HIGH", "MID", "LOW"})
			String level,
			@Schema(description = "판단에 크게 작용한 평가요소(ACADEMIC, MAJOR_FIT, COMMUNITY). 최대 2개.")
			List<String> basis
	) {
	}

	/** 요청 과목 행 하나. 사용자가 입력한 5등급 값을 그대로 돌려준다. */
	public record SubjectResult(
			@Schema(description = "요청의 subjectRef 그대로", example = "row-1")
			String subjectRef,
			@Schema(description = "요청의 subjectCode 그대로", example = "SUBJ_0001")
			String subjectCode,
			@Schema(description = "입력한 5등급 값 그대로", example = "2")
			BigDecimal grade
	) {
	}

	/**
	 * 추천 원서 한 장.
	 *
	 * @param category      합격 가능성 구간(SAFE, MATCH, REACH, FAR, OVER, UNRATED)
	 * @param candidateKind 후보가 오른 경로(DESIRED_MAJOR, SIMILAR_MAJOR, OTHER_TRACK, UNDECLARED)
	 * @param gradeGap      예측 70%컷(5등급 환산)에서 사용자 평균을 뺀 값. 소수 둘째 자리. UNRATED면 null
	 */
	public record RecommendationItem(
			@Schema(description = "추천 순서(지망 순위)", example = "1")
			int rank,
			@Schema(description = "대학 이름")
			String university,
			@Schema(description = "학과(모집단위) 이름")
			String department,
			@Schema(description = "전형 이름")
			String admissionType,
			@Schema(description = "합격 가능성 구간. SAFE=안정, MATCH=적정, REACH=소신, FAR=도전 부담이 큰 후보, OVER=성적 여유가 있는 후보, UNRATED=판단 자료 부족. 합격 확률이 아니라 참고 구간이다.", allowableValues = {"SAFE", "MATCH", "REACH", "FAR", "OVER", "UNRATED"})
			String category,
			@Schema(description = "후보가 추천에 오른 경로", allowableValues = {"DESIRED_MAJOR", "SIMILAR_MAJOR", "OTHER_TRACK", "UNDECLARED"})
			String candidateKind,
			@Schema(description = "예측 70%컷(5등급 환산)에서 사용자 평균을 뺀 값(소수 둘째 자리). 양수면 컷보다 여유, 음수면 부족. UNRATED면 null.", nullable = true)
			BigDecimal gradeGap,
			@Schema(description = "추천 근거. 적재되지 않은 값은 null.")
			Evidence evidence,
			@Schema(description = "카드별 추천 이유")
			Explanation reason,
			@Schema(description = "서류를 반영해 조정한 구간(SAFE, MATCH, REACH). 서류 보정을 적용한 카드만 값이 있고 나머지는 null. 이때 category 는 성적만으로 본 구간이다.", allowableValues = {"SAFE", "MATCH", "REACH"}, nullable = true)
			String overallCategory,
			@Schema(description = "서류 적합도. 학종 후보에 서류 입력이 있을 때만 값이 있고 나머지는 null.", nullable = true)
			DocumentFitItem documentFit
	) {

		/** 서류 반영 정보가 없는 카드 */
		public RecommendationItem(
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
			this(rank, university, department, admissionType, category, candidateKind, gradeGap, evidence, reason,
					null, null);
		}
	}

	/**
	 * 추천 근거. 적재되지 않은 값은 null이다.
	 *
	 * 학과 신설·통합 여부는 FE와 합의한 이름(isNewOrMergedDepartment)으로 내려간다.
	 * 이름이 is 로 시작하는 Boolean 컴포넌트가 JSON 키에서 is 가 빠지지 않도록 키를 직접 지정한다.
	 */
	public record Evidence(
			@Schema(description = "모집 인원. 값이 없으면 null.", nullable = true)
			Integer quota,
			@Schema(description = "경쟁률. 값이 없으면 null.", nullable = true)
			Double competitionRate,
			@Schema(description = "학과 신설·통합 여부. 값이 없으면 null.", nullable = true)
			@JsonProperty("isNewOrMergedDepartment") Boolean isNewOrMergedDepartment,
			@Schema(description = "예측에 쓴 입결 연도 수")
			int basisYears
	) {
	}

	/** 설명 문장과 생성 방식(template 또는 llm). */
	public record Explanation(
			@Schema(description = "설명 문장")
			String text,
			@Schema(description = "생성 방식. template=템플릿 문장, llm=AI가 만들고 검증을 통과한 문장. 섹션마다 다를 수 있다.", allowableValues = {"template", "llm"})
			String generationMethod
	) {
	}

	/**
	 * 안내 한 건. 화면 분기는 code로 하고 message는 문구만 바뀔 수 있는 값이다.
	 *
	 * @param rank 해당 카드의 지망 순서. 결과 전체에 해당하면 null
	 */
	public record WarningItem(
			@Schema(description = "안내 코드. 화면 분기는 이 값으로 한다.", example = "FEWER_THAN_SIX_CARDS")
			String code,
			@Schema(description = "해당 카드의 rank. 결과 전체에 해당하면 null.", nullable = true)
			Integer rank,
			@Schema(description = "안내 문구. 문구만 바뀔 수 있으니 분기에 쓰지 않는다.")
			String message
	) {
	}

	public record ReportBody(
			@Schema(description = "인사·요약")
			Explanation greetingSummary,
			@Schema(description = "참고 평균과 자료 한계에 대한 총평")
			Explanation overallAssessment,
			@Schema(description = "지원 검토 전략")
			Explanation applicationStrategy,
			@Schema(description = "마무리 안내")
			Explanation closing
	) {
	}

}
