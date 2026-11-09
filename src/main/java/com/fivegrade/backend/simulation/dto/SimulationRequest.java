package com.fivegrade.backend.simulation.dto;

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
 * @param subjects           과목별 성적. 최소 1개
 * @param applicationPreference 지원 방향
 * @param analysisConsent    분석 동의 여부. true 가 아니면 거부한다. 원시 boolean 이 아니라 Boolean 인 이유는
 *                           필드가 빠진 요청도 다른 검증 실패와 같은 형식(필드 이름 포함)으로 거부하기 위해서다.
 */
public record SimulationRequest(

		@NotEmpty
		@Size(max = SimulationRequest.MAX_SUBJECTS)
		List<@NotNull @Valid SubjectInput> subjects,

		@NotNull
		@Valid
		ApplicationPreference applicationPreference,

		@NotNull
		@AssertTrue
		Boolean analysisConsent

) {

	/** 한 요청의 과목 행 상한. 최대 과목 수가 확정되기 전까지 과도한 입력을 막는 안전 상한이다. */
	static final int MAX_SUBJECTS = 100;

}
