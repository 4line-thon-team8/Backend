package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.common.error.ErrorResponse;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 생기부 분석 리포트 API. 시뮬레이션과 분리해서, 리포트 생성이 추천 응답을 느리게 하거나 실패시키지 않게 한다.
 * 로그인이 필요하다(/api/** 기본 설정). 결과는 저장하지 않는다.
 */
@RestController
@RequestMapping("/api/holistic-reports")
public class HolisticReportController {

	private final HolisticReportService service;

	public HolisticReportController(HolisticReportService service) {
		this.service = service;
	}

	@Operation(
			summary = "생기부 분석 리포트 생성",
			description = "기기에서 분석한 서술 신호(시뮬레이션의 holisticAnalysis 와 같은 형식)로 요소·지표별 구간과 글 리포트를 돌려준다. "
					+ "서술 분석을 반영하지 못하면 status 가 SKIPPED 이고 skipReason 으로 이유를 알린다. "
					+ "근거 구절(evidence)은 리포트에 쓰지 않으며 저장하지 않는다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "리포트 또는 반영하지 못한 이유",
					content = @Content(schema = @Schema(implementation = HolisticReportResponse.class))),
			@ApiResponse(responseCode = "400", description = "입력 오류",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "유효한 토큰 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping
	public ResponseEntity<HolisticReportResponse> create(@Valid @RequestBody HolisticAnalysisRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(service.create(request));
	}

}
