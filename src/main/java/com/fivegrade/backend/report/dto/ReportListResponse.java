package com.fivegrade.backend.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 저장 리포트 목록 응답. 배열을 바로 내려주지 않고 객체로 감싸서, 나중에 필드를 추가해도 FE 가 깨지지 않게 한다.
 */
public record ReportListResponse(
		@Schema(description = "내 저장 리포트 목록(최신순). 없으면 빈 배열. 추천 원서와 이유는 상세 API에서 조회한다.")
		List<ReportSummary> reports
) {

}
