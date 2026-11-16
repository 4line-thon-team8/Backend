package com.fivegrade.backend.holistic.service;

import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;

/**
 * 서술 분석 요청을 평가한다. SimulationService 는 이 인터페이스에만 의존한다.
 */
@FunctionalInterface
public interface HolisticEvaluator {

	/** @param request 서술 분석 요청. 보내지 않았으면 null */
	HolisticEvaluation evaluate(HolisticAnalysisRequest request);

	/** 서술 분석을 쓰지 않는 평가기. 언제나 "보내지 않음"으로 답한다. */
	HolisticEvaluator NONE = request ->
			new HolisticEvaluation.Skipped(HolisticEvaluation.SkipReason.NOT_PROVIDED);

}
