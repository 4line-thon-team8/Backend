package com.fivegrade.backend.simulation.controller;

import com.fivegrade.backend.common.error.ErrorResponse;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import com.fivegrade.backend.simulation.dto.SimulationRequest;
import com.fivegrade.backend.simulation.dto.SimulationResponse;
import com.fivegrade.backend.simulation.service.SimulationReportAssembler;
import com.fivegrade.backend.simulation.service.SimulationRequestValidator;
import com.fivegrade.backend.simulation.service.SimulationResponseMapper;
import com.fivegrade.backend.simulation.service.SimulationService;
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
 * POST /api/simulations. 성적과 지원 방향으로 평균·추천·리포트를 만들어 돌려준다.
 *
 * 처리 순서는 DTO 형식 검증 → 희망 학과 검증 → 과목 코드 검증 → 평균·추천 → 설명 조립 → 응답 변환이다.
 * 검증을 통과하기 전에는 평균과 추천 계산을 시작하지 않는다.
 * 성적과 희망 학과 값은 로그나 오류 메시지에 남기지 않는다.
 *
 * 리포트 저장은 저장 응답 계약(#39) 확정 후에 이 흐름 뒤에 붙인다.
 */
@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

	private final SimulationRequestValidator validator;
	private final SimulationService simulationService;
	private final SimulationReportAssembler assembler;

	public SimulationController(
			SimulationRequestValidator validator,
			SimulationService simulationService,
			SimulationReportAssembler assembler
	) {
		this.validator = validator;
		this.simulationService = simulationService;
		this.assembler = assembler;
	}

	@Operation(
			summary = "분석 요청",
			description = "5등급 성적과 지원 방향으로 참고 평균, 추천 원서(0~6장), 컨설팅 리포트를 돌려준다. "
					+ "추천 후보가 6장에 못 미치면 있는 만큼만 돌려주고 warnings 로 알린다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "분석 결과",
					content = @Content(schema = @Schema(implementation = SimulationResponse.class))),
			@ApiResponse(responseCode = "400", description = "입력 오류",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "유효한 토큰 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "서버 오류",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping
	public ResponseEntity<SimulationResponse> analyze(@Valid @RequestBody SimulationRequest request) {
		validator.validateDesiredMajors(request.applicationPreference());
		validator.validateSubjects(request.subjects());

		SimulationAnalysis analysis = simulationService.analyzeValidated(request);
		SimulationReportResult result = assembler.assemble(analysis);

		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(SimulationResponseMapper.toResponse(result));
	}

}
