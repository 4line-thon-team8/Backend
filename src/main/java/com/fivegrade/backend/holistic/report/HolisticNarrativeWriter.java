package com.fivegrade.backend.holistic.report;

/**
 * 활동 요약·학과별 조언을 쓴다. 구현체는 실패해도 예외를 밖으로 내지 않고, 항상 결과를 돌려준다.
 * 글마다 생성 방식(TEMPLATE/LLM)이 붙으므로 일부만 LLM이고 나머지가 기본 문장일 수 있다.
 */
public interface HolisticNarrativeWriter {

	HolisticNarrative write(HolisticNarrativeFacts facts);

}
