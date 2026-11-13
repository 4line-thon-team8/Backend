package com.fivegrade.backend.simulation.controller;

import com.fivegrade.backend.auth.model.AuthenticatedUser;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.common.error.ErrorResponse;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import com.fivegrade.backend.simulation.dto.SimulationRequest;
import com.fivegrade.backend.simulation.dto.SimulationResponse;
import com.fivegrade.backend.simulation.service.SimulationReportAssembler;
import com.fivegrade.backend.simulation.service.SimulationReportSaver;
import com.fivegrade.backend.simulation.service.SimulationRequestValidator;
import com.fivegrade.backend.simulation.service.SimulationResponseMapper;
import com.fivegrade.backend.simulation.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/simulations. 성적과 지원 방향으로 평균·추천·리포트를 만들어 돌려준다.
 *
 * 처리 순서는 DTO 형식 검증 → 희망 학과 검증 → 과목 코드 검증 → 평균·추천 → 설명 조립 → 응답 변환이다.
 * 검증을 통과하기 전에는 평균과 추천 계산을 시작하지 않는다.
 * 성적과 희망 학과 값은 로그나 오류 메시지에 남기지 않는다.
 *
 * 분석이 끝나면 로그인 사용자의 리포트 최소 항목을 자동 저장한다(#39). 저장이 실패해도 분석 응답은 그대로 내려가고,
 * 추천이 0장이면 저장하지 않는다. 저장 결과를 응답에 담는 필드(saveStatus 등)는 #61 확정 후에 추가한다.
 */
@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

	/** reports.idempotency_key 컬럼 길이 */
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

	private final SimulationRequestValidator validator;
	private final SimulationService simulationService;
	private final SimulationReportAssembler assembler;
	private final SimulationReportSaver reportSaver;

	public SimulationController(
			SimulationRequestValidator validator,
			SimulationService simulationService,
			SimulationReportAssembler assembler,
			SimulationReportSaver reportSaver
	) {
		this.validator = validator;
		this.simulationService = simulationService;
		this.assembler = assembler;
		this.reportSaver = reportSaver;
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
	public ResponseEntity<SimulationResponse> analyze(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Parameter(description = "같은 값으로 다시 요청하면 리포트를 중복 저장하지 않는다. 생략하면 매 요청을 새로 저장한다. 최대 100자.")
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody SimulationRequest request
	) {
		String reportKey = resolveIdempotencyKey(idempotencyKey);
		validator.validateDesiredMajors(request.applicationPreference());
		validator.validateSubjects(request.subjects());

		SimulationAnalysis analysis = simulationService.analyzeValidated(request);
		SimulationReportResult result = assembler.assemble(analysis);
		reportSaver.save(user.id(), reportKey, result);

		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(SimulationResponseMapper.toResponse(result));
	}

	/** 헤더가 없으면 요청마다 새 키를 만들어 저장한다. 헤더가 있는데 비었거나 너무 길면 400이다. */
	private static String resolveIdempotencyKey(String header) {
		if (header == null) {
			return UUID.randomUUID().toString();
		}
		if (header.isBlank() || header.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "입력값이 올바르지 않습니다: Idempotency-Key");
		}
		return header;
	}

}
