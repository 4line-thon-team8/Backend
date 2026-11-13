package com.fivegrade.backend.report.controller;

import com.fivegrade.backend.auth.model.AuthenticatedUser;
import com.fivegrade.backend.common.error.ErrorResponse;
import com.fivegrade.backend.report.dto.ReportDetail;
import com.fivegrade.backend.report.dto.ReportListResponse;
import com.fivegrade.backend.report.service.ReportStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 저장 리포트 조회·삭제 (#39, B-12). 저장은 POST /api/simulations 가 분석 직후 자동으로 한다.
 *
 * 현재 사용자는 인증에서 확인된 값만 쓰고, 요청 본문이나 경로의 userId 는 받지 않는다.
 * 없는 리포트는 404, 남의 리포트는 403, 토큰이 없으면 401 이다.
 * 응답에는 성적 숫자와 환산 결과가 없다(저장하지 않는다).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

	private final ReportStorageService reportStorageService;

	public ReportController(ReportStorageService reportStorageService) {
		this.reportStorageService = reportStorageService;
	}

	@Operation(summary = "내 리포트 목록", description = "본인이 저장한 리포트를 최신순으로 돌려준다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "리포트 목록(없으면 빈 배열)",
					content = @Content(schema = @Schema(implementation = ReportListResponse.class))),
			@ApiResponse(responseCode = "401", description = "유효한 토큰 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping
	public ResponseEntity<ReportListResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(new ReportListResponse(reportStorageService.list(user.id())));
	}

	@Operation(summary = "리포트 상세", description = "저장된 추천 원서와 추천 이유를 돌려준다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "리포트 상세",
					content = @Content(schema = @Schema(implementation = ReportDetail.class))),
			@ApiResponse(responseCode = "401", description = "유효한 토큰 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "다른 사용자의 리포트",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "없는 리포트",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/{reportId}")
	public ResponseEntity<ReportDetail> get(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID reportId
	) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(reportStorageService.get(user.id(), reportId));
	}

	@Operation(summary = "리포트 삭제", description = "본인 리포트와 그 추천 원서를 삭제한다.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "삭제 완료", content = @Content),
			@ApiResponse(responseCode = "401", description = "유효한 토큰 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "다른 사용자의 리포트",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "없는 리포트",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@DeleteMapping("/{reportId}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID reportId
	) {
		reportStorageService.delete(user.id(), reportId);
		return ResponseEntity.noContent().build();
	}

}
