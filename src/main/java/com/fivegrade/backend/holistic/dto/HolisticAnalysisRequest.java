package com.fivegrade.backend.holistic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * POST /api/simulations 요청의 선택 필드 {@code holisticAnalysis}. 기기 안에서 분석한 생기부 서술 신호다.
 *
 * 서술 전문은 서버로 오지 않는다. 지표별 원시 측정값과 이름·학교를 가린 짧은 구절만 받는다.
 * 지표 수준(1~5)은 FE가 아니라 BE가 계산하므로 여기에는 수준 값이 없다.
 * 형식 검증만 여기서 하고, 지표 ID·신호 유형·태그가 사전에 있는지는 {@code HolisticRequestValidator} 가 확인한다.
 *
 * 근거 구절은 개인정보가 섞일 수 있어 toString 에 담지 않는다. 예외 메시지나 로그에 구절이 남지 않게 하기 위해서다.
 *
 * @param rulesVersion    FE가 분석에 쓴 규칙 사전 버전
 * @param coverage        영역별로 읽은 글자 수와 학년
 * @param indicatorSignals 지표별 원시 측정값
 * @param interestTags    전공 키워드 태그별 빈도
 * @param evidence        이름·학교를 가린 근거 구절
 */
public record HolisticAnalysisRequest(

		@Schema(description = "FE가 분석에 쓴 규칙 사전 버전(GET /api/holistic-rules 의 rulesVersion).", example = "0.1.0")
		@NotBlank
		@Size(max = 20)
		String rulesVersion,

		@NotNull
		@Valid
		Coverage coverage,

		@Schema(description = "지표별 원시 측정값. 최대 20개이며 같은 지표 ID는 겹칠 수 없다.")
		@NotNull
		@Size(max = HolisticAnalysisRequest.MAX_INDICATOR_SIGNALS)
		List<@NotNull @Valid IndicatorSignal> indicatorSignals,

		@Schema(description = "전공 키워드 태그별 빈도. 최대 30개이며 태그는 /api/tag-map 의 값만 가능하다.")
		@NotNull
		@Size(max = HolisticAnalysisRequest.MAX_INTEREST_TAGS)
		List<@NotNull @Valid InterestTag> interestTags,

		@Schema(description = "이름·학교를 가린 근거 구절. 최대 150개, 구절은 30자 이하이며 서버에 저장하지 않는다.")
		@NotNull
		@Size(max = HolisticAnalysisRequest.MAX_EVIDENCE)
		List<@NotNull @Valid EvidencePhrase> evidence

) {

	static final int MAX_INDICATOR_SIGNALS = 20;
	static final int MAX_INTEREST_TAGS = 30;
	static final int MAX_EVIDENCE = 150;
	static final int MAX_EVIDENCE_TEXT = 30;
	static final int MAX_SECTION_CHARS = 200_000;

	/**
	 * @param sectionChars 영역별로 읽은 글자 수. 서술이 부족한지 판단하는 데만 쓴다.
	 * @param years        분석에 쓰인 학년(1~3)
	 */
	public record Coverage(

			@NotNull
			@Valid
			SectionChars sectionChars,

			@Schema(description = "분석에 쓰인 학년(1~3). 중복 없이 최대 3개.")
			@NotNull
			@Size(max = 3)
			List<@NotNull @Min(1) @Max(3) Integer> years

	) {
	}

	/** 영역 5개의 글자 수. 빠진 영역은 0으로 보내야 한다. */
	public record SectionChars(

			@NotNull @Min(0) @Max(HolisticAnalysisRequest.MAX_SECTION_CHARS) Integer autonomy,
			@NotNull @Min(0) @Max(HolisticAnalysisRequest.MAX_SECTION_CHARS) Integer club,
			@NotNull @Min(0) @Max(HolisticAnalysisRequest.MAX_SECTION_CHARS) Integer career,
			@NotNull @Min(0) @Max(HolisticAnalysisRequest.MAX_SECTION_CHARS) Integer subject,
			@NotNull @Min(0) @Max(HolisticAnalysisRequest.MAX_SECTION_CHARS) Integer behavior

	) {

		/** 다섯 영역 글자 수의 합 */
		public long total() {
			return (long) autonomy + club + career + subject + behavior;
		}
	}

	/**
	 * @param id      지표 ID. 규칙 사전의 지표여야 한다.
	 * @param count   신호 수
	 * @param years   신호가 나타난 학년(1~3)
	 * @param quality 구체성·단계 충족 같은 질 점수(0~1)
	 */
	public record IndicatorSignal(

			@NotBlank @Size(max = 10) String id,
			@NotNull @Min(0) @Max(1000) Integer count,

			@NotNull
			@Size(min = 1, max = 3)
			List<@NotNull @Min(1) @Max(3) Integer> years,

			@NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double quality

	) {
	}

	/**
	 * @param tag   전공 키워드 태그 이름
	 * @param count 빈도
	 * @param years 나타난 학년(1~3)
	 */
	public record InterestTag(

			@NotBlank @Size(max = 50) String tag,
			@NotNull @Min(0) @Max(1000) Integer count,

			@NotNull
			@Size(min = 1, max = 3)
			List<@NotNull @Min(1) @Max(3) Integer> years

	) {
	}

	/**
	 * @param types   구절 유형(여러 개 가능). 규칙 사전의 유형이어야 한다.
	 * @param text    이름·학교를 가린 짧은 구절
	 * @param year    학년(1~3). 구분하지 못했으면 null
	 * @param section 영역 코드(autonomy, club, career, subject, behavior)
	 */
	public record EvidencePhrase(

			@NotNull
			@NotEmpty
			@Size(max = 10)
			List<@NotBlank @Size(max = 20) String> types,

			@NotBlank @Size(max = HolisticAnalysisRequest.MAX_EVIDENCE_TEXT) String text,

			@Min(1) @Max(3) Integer year,

			@NotBlank
			@Pattern(regexp = "autonomy|club|career|subject|behavior")
			String section

	) {

		/** 구절 본문은 개인정보가 섞일 수 있어 로그·예외에 나가지 않게 가린다. */
		@Override
		public String toString() {
			return "EvidencePhrase[types=" + types + ", section=" + section + ", year=" + year + "]";
		}
	}

	@Override
	public String toString() {
		return "HolisticAnalysisRequest[rulesVersion=" + rulesVersion
				+ ", indicatorSignals=" + (indicatorSignals == null ? 0 : indicatorSignals.size())
				+ ", interestTags=" + (interestTags == null ? 0 : interestTags.size())
				+ ", evidence=" + (evidence == null ? 0 : evidence.size()) + "]";
	}

}
