package com.fivegrade.backend.report.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 저장 결과.
 *
 * @param id        리포트 ID
 * @param createdAt 저장된 시각
 * @param created   이번 호출에서 새로 저장했으면 true, 같은 키로 이미 저장된 리포트를 돌려줬으면 false
 */
public record SavedReport(UUID id, Instant createdAt, boolean created) {

}
