package com.fivegrade.backend.holistic.rules;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * 학종 서술 분석 규칙 사전 조회 API. FE 가 서술 분석 직전에 받아 캐시하며, 로그인 없이 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/holistic-rules")
public class HolisticRulesController {

	private final HolisticRulesResponse response;
	private final String eTag;

	public HolisticRulesController(HolisticRuleCatalog catalog) {
		this.response = HolisticRulesResponse.from(catalog);
		// 사전 내용이 바뀌면 값이 달라진다. 규칙 버전도 함께 넣어 사람이 헤더만 보고 구분할 수 있게 한다.
		this.eTag = "\"" + catalog.rulesVersion() + "-" + catalog.contentHash() + "\"";
	}

	@Operation(
			summary = "학종 서술 분석 규칙 사전 조회",
			description = "기기 안 서술 분석에 쓰는 지표·신호 유형·구절 추출 규칙을 돌려준다. rulesVersion 은 분석 요청에 그대로 "
					+ "실어 보내야 하며, 서버가 받아 주는 버전과 다르면 서술 분석이 반영되지 않는다. "
					+ "ETag 를 지원하므로 If-None-Match 로 보내면 바뀌지 않았을 때 304 로 답한다. "
					+ "점수 임계값과 가중치는 내려주지 않는다. 로그인 없이 호출할 수 있다.")
	@GetMapping
	public ResponseEntity<HolisticRulesResponse> getRules(WebRequest request) {
		if (request.checkNotModified(eTag)) {
			// 304 응답은 checkNotModified 가 이미 채웠으므로 본문 없이 돌려준다.
			return null;
		}
		return ResponseEntity.ok()
				.eTag(eTag)
				.cacheControl(CacheControl.noCache())
				.body(response);
	}

}
