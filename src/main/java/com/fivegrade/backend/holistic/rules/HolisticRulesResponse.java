package com.fivegrade.backend.holistic.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * GET /api/holistic-rules 응답. FE가 기기 안 서술 분석에 쓰는 공개 규칙 사전이다.
 * 점수 임계값과 가중치, 규칙 예시와 출처는 BE 전용이라 담지 않는다.
 */
public record HolisticRulesResponse(

		@Schema(description = "규칙 사전 버전. 분석 요청의 holisticAnalysis.rulesVersion 에 그대로 실어 보낸다.", example = "0.1.0")
		String rulesVersion,

		@Schema(description = "요청 한도. FE 분석이 이 안에서 만든다.")
		HolisticRuleCatalog.Limits limits,

		@Schema(description = "신호(구절) 유형 목록")
		List<String> types,

		@Schema(description = "분석 지표 목록")
		List<HolisticRuleCatalog.Indicator> indicators,

		@Schema(description = "구절 추출 규칙 목록")
		List<RuleView> rules

) {

	/** 공개 규칙. 가중치·예시·출처를 뺀 모양이다. */
	public record RuleView(
			String id,
			String indicator,
			List<String> types,
			List<String> sections,
			String pattern,
			Integer within,
			List<String> excludes
	) {
	}

	public static HolisticRulesResponse from(HolisticRuleCatalog catalog) {
		List<RuleView> rules = catalog.rules().stream()
				.map(rule -> new RuleView(rule.id(), rule.indicator(), rule.types(), rule.sections(),
						rule.pattern(), rule.within(), rule.excludes()))
				.toList();
		return new HolisticRulesResponse(
				catalog.rulesVersion(), catalog.limits(), catalog.types(), catalog.indicators(), rules);
	}

}
