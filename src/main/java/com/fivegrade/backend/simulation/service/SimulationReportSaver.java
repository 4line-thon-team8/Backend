package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.report.dto.RecommendationDraft;
import com.fivegrade.backend.report.dto.ReportDraft;
import com.fivegrade.backend.report.dto.SavedReport;
import com.fivegrade.backend.report.service.ReportStorageService;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 분석 결과에서 저장에 필요한 최소 항목만 뽑아 리포트로 자동 저장한다 (#39).
 *
 * 저장하는 것은 추천 원서(대학·학과·전형·구간 라벨·추천 이유·전형 참조 ID)뿐이다(기능명세 3.3).
 * 인사·요약 문장은 희망 학과와 선호 전형을 요약한 문장이라 저장하지 않는다. 저장 여부를 나중에 바꿀 수 있게
 * 컬럼(greeting_summary)과 ReportDraft 필드는 남겨 두고 null 로 채운다.
 * 성적, 평균, 환산 결과, 요청 원본, 전체 report 는 저장하지 않는다(P-04).
 *
 * 저장은 부가 기능이라 실패해도 분석 응답을 막지 않는다. 실패하면 예외를 던지지 않고 FAILED 로 알린다.
 * 로그에는 예외 종류만 남기고 메시지·저장 내용은 남기지 않는다.
 * 추천이 0장이면 저장할 내용이 없으므로 저장하지 않는다.
 */
public class SimulationReportSaver {

	private static final Logger log = LoggerFactory.getLogger(SimulationReportSaver.class);

	private final ReportStorageService storageService;

	public SimulationReportSaver(ReportStorageService storageService) {
		this.storageService = storageService;
	}

	public SaveOutcome save(Long userId, String idempotencyKey, SimulationReportResult result) {
		if (result.recommendations().isEmpty()) {
			return new SaveOutcome(Status.SKIPPED_NO_CARDS, null);
		}
		try {
			SavedReport saved = storageService.save(userId, idempotencyKey, toDraft(result));
			return new SaveOutcome(saved.created() ? Status.SAVED : Status.ALREADY_SAVED, saved.id());
		} catch (RuntimeException e) {
			log.warn("분석 리포트 자동 저장에 실패했습니다. cause={}", e.getClass().getSimpleName());
			return new SaveOutcome(Status.FAILED, null);
		}
	}

	/** 내부 분석 결과를 최소 저장 계약으로 옮긴다. 결과 객체 전체를 직렬화하지 않는다. */
	static ReportDraft toDraft(SimulationReportResult result) {
		List<RecommendationDraft> items = result.recommendations().stream()
				.map(explained -> new RecommendationDraft(
						explained.card().rank(),
						explained.card().universityName(),
						explained.card().departmentName(),
						explained.card().admissionTypeName(),
						explained.card().band().name(),
						explained.reason().text(),
						explained.card().unitId()))
				.toList();
		return new ReportDraft(null, items);
	}

	public enum Status {
		/** 새로 저장했다. */
		SAVED,
		/** 같은 Idempotency-Key 로 이미 저장돼 있어 먼저 저장된 리포트를 가리킨다. */
		ALREADY_SAVED,
		/** 추천이 0장이라 저장하지 않았다. */
		SKIPPED_NO_CARDS,
		/** 저장에 실패했다. 분석 응답은 그대로 내려간다. */
		FAILED
	}

	/** 저장 결과. FAILED·SKIPPED_NO_CARDS 이면 reportId 는 null 이다. */
	public record SaveOutcome(Status status, UUID reportId) {

	}

}
