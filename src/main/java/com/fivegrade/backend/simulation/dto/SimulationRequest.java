package com.fivegrade.backend.simulation.dto;

import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * POST /api/simulations 요청 본문.
 *
 * 정의하지 않은 필드는 받지 않는다(application.yaml 의 fail-on-unknown-properties).
 * 성적과 지원 방향 값은 응답 메시지와 로그에 남기지 않는다. 검증 실패는 필드 이름만 알린다.
 *
 * 전공 키워드 태그(keywordTags)와 통계 수집 동의(statisticsConsent)는 3단계에서
 * api-change 이슈로 합의한 뒤 추가한다.
 *
 * @param subjects           과목별 성적. 최소 1개이고 subjectRef는 겹칠 수 없다.
 *                           같은 subjectCode는 여러 행(다른 학기 등)으로 보낼 수 있다.
 * @param applicationPreference 지원 방향
 * @param analysisConsent    분석 동의 여부. true 가 아니면 거부한다. 원시 boolean 이 아니라 Boolean 인 이유는
 *                           필드가 빠진 요청도 다른 검증 실패와 같은 형식(필드 이름 포함)으로 거부하기 위해서다.
 * @param holisticAnalysis   기기 안에서 분석한 생기부 서술 신호. 선택 필드이며 없으면(키를 보내지 않으면) 기존과 똑같이 동작한다.
 *                           서술을 읽지 못했을 때는 null 이 아니라 키를 생략한다.
 */
public record SimulationRequest(

		@Schema(description = "과목별 5등급제 성적. 최소 1개, 최대 100개. subjectRef는 요청 안에서 겹칠 수 없고, 같은 subjectCode는 여러 행으로 보낼 수 있다.")
		@NotEmpty
		@Size(max = SimulationRequest.MAX_SUBJECTS)
		@UniqueSubjectRefs
		List<@NotNull @Valid SubjectInput> subjects,

		@Schema(description = "지원 방향(희망 학과, 선호 전형, 옵션)")
		@NotNull
		@Valid
		ApplicationPreference applicationPreference,

		@Schema(description = "분석 동의. 반드시 true여야 하며 false이거나 빠지면 400.", example = "true")
		@NotNull
		@AssertTrue
		Boolean analysisConsent,

		@Schema(description = "기기 안 서술 분석 신호(선택). 키를 생략하면 성적만으로 분석한다. 규칙 버전은 GET /api/holistic-rules 와 같아야 반영된다.")
		@Valid
		HolisticAnalysisRequest holisticAnalysis

) {

	/** 한 요청의 과목 행 상한. 최대 과목 수가 확정되기 전까지 과도한 입력을 막는 안전 상한이다. */
	static final int MAX_SUBJECTS = 100;

}
