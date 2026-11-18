package com.fivegrade.backend.holistic.report;

import java.util.Map;

/** AI 호출 경계. 입력 JSON 구조를 받아 검증 전 초안을 돌려준다. 실패는 예외로 알린다. */
public interface HolisticReportAiClient {

	ParsedReport generate(Map<String, Object> input) throws Exception;

}
