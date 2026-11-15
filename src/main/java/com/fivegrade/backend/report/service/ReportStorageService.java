package com.fivegrade.backend.report.service;

import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.report.dto.RecommendationDraft;
import com.fivegrade.backend.report.dto.ReportDetail;
import com.fivegrade.backend.report.dto.ReportDraft;
import com.fivegrade.backend.report.dto.ReportSummary;
import com.fivegrade.backend.report.dto.SavedReport;
import com.fivegrade.backend.report.entity.Report;
import com.fivegrade.backend.report.repository.ReportRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그인 사용자의 리포트 저장·조회·삭제 (B-12).
 *
 * 현재 사용자는 호출하는 쪽(컨트롤러)이 인증으로 알아내 userId 로 넘긴다. 이 서비스는 인증을 모른다.
 * 성적 숫자, 환산 결과, 요청 원본은 받지도 저장하지도 않는다. 저장 내용은 로그에 남기지 않고,
 * 오류 메시지에도 사용자가 보낸 값을 넣지 않는다.
 */
@Service
public class ReportStorageService {

	/** 수시 지원 한도 기준으로 리포트에 담는 추천 원서는 최대 6장이다. */
	static final int MAX_RECOMMENDATIONS = 6;

	/** reports.idempotency_key 컬럼 길이 */
	private static final int MAX_KEY_LENGTH = 100;

	private static final int MAX_NAME_LENGTH = 100;
	private static final int MAX_LABEL_LENGTH = 30;

	private final ReportRepository reportRepository;

	/**
	 * 저장 시도는 호출하는 쪽의 트랜잭션과 분리한다.
	 * 저장이 실패해도 분석 응답을 만드는 쪽의 트랜잭션이 롤백 표시되지 않게 하기 위해서다.
	 */
	private final TransactionTemplate isolatedTx;

	public ReportStorageService(ReportRepository reportRepository, PlatformTransactionManager transactionManager) {
		this.reportRepository = reportRepository;
		this.isolatedTx = new TransactionTemplate(transactionManager);
		this.isolatedTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * 리포트를 저장한다. 같은 사용자가 같은 Idempotency-Key 로 다시 호출하면
	 * 새로 만들지 않고 먼저 저장된 리포트를 그대로 돌려준다(created=false).
	 *
	 * 두 요청이 동시에 들어오면 DB 유니크 제약이 한 건만 남기고, 진 쪽은 먼저 저장된 것을 읽어 돌려준다.
	 * 저장에 실패하면 예외를 던진다. 분석 응답을 그대로 내려줄지는 호출하는 쪽이 정한다.
	 */
	public SavedReport save(Long userId, String idempotencyKey, ReportDraft draft) {
		validate(userId, idempotencyKey, draft);

		Optional<SavedReport> existing = isolatedTx.execute(status -> findSaved(userId, idempotencyKey));
		if (existing.isPresent()) {
			return existing.get();
		}

		try {
			return isolatedTx.execute(status -> insert(userId, idempotencyKey, draft));
		} catch (DataIntegrityViolationException e) {
			// 동시 요청이 먼저 저장했다면 그 리포트를 돌려준다. 다른 제약 위반이면 원래 예외를 그대로 던진다.
			Optional<SavedReport> winner = isolatedTx.execute(status -> findSaved(userId, idempotencyKey));
			if (winner.isPresent()) {
				return winner.get();
			}
			throw e;
		}
	}

	/** 본인 리포트 목록을 최신순으로 돌려준다. */
	@Transactional(readOnly = true)
	public List<ReportSummary> list(Long userId) {
		return reportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(ReportSummary::from)
				.toList();
	}

	/** 리포트 한 건을 조회한다. 없으면 404, 남의 것이면 403 이다. */
	@Transactional(readOnly = true)
	public ReportDetail get(Long userId, UUID reportId) {
		return ReportDetail.from(findOwned(userId, reportId));
	}

	/** 리포트를 삭제한다. 추천 원서도 함께 지워진다. 없으면 404, 남의 것이면 403 이다. */
	@Transactional
	public void delete(Long userId, UUID reportId) {
		reportRepository.delete(findOwned(userId, reportId));
	}

	/** 회원 탈퇴용. 해당 사용자의 리포트를 모두 지우고 지운 개수를 돌려준다. */
	@Transactional
	public long deleteAllByUser(Long userId) {
		return reportRepository.deleteByUserId(userId);
	}

	private Optional<SavedReport> findSaved(Long userId, String idempotencyKey) {
		return reportRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
				.map(report -> new SavedReport(report.getId(), report.getCreatedAt(), false));
	}

	private SavedReport insert(Long userId, String idempotencyKey, ReportDraft draft) {
		Report report = new Report(userId, idempotencyKey, draft.greetingSummary());
		for (RecommendationDraft item : draft.recommendations()) {
			report.addRecommendation(item.rank(), item.universityName(), item.deptName(),
					item.trackName(), item.label(), item.reason(), item.unitId());
		}
		Report saved = reportRepository.saveAndFlush(report);
		return new SavedReport(saved.getId(), saved.getCreatedAt(), true);
	}

	private Report findOwned(Long userId, UUID reportId) {
		Report report = reportRepository.findById(reportId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
		if (!report.getUserId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		return report;
	}

	/** 형식 검증. 메시지에는 어느 값이 문제인지 이름만 담고 보낸 값은 담지 않는다. */
	private void validate(Long userId, String idempotencyKey, ReportDraft draft) {
		if (userId == null) {
			throw invalid("userId");
		}
		if (isBlank(idempotencyKey) || idempotencyKey.length() > MAX_KEY_LENGTH) {
			throw invalid("idempotencyKey");
		}
		if (draft == null) {
			throw invalid("report");
		}
		List<RecommendationDraft> items = draft.recommendations();
		if (items == null || items.isEmpty() || items.size() > MAX_RECOMMENDATIONS) {
			throw invalid("recommendations");
		}

		Set<Integer> ranks = new HashSet<>();
		Set<String> targets = new HashSet<>();
		for (RecommendationDraft item : items) {
			if (item == null) {
				throw invalid("recommendations");
			}
			if (item.rank() < 1 || item.rank() > MAX_RECOMMENDATIONS || !ranks.add(item.rank())) {
				throw invalid("recommendations.rank");
			}
			requireText(item.universityName(), MAX_NAME_LENGTH, "recommendations.universityName");
			requireText(item.deptName(), MAX_NAME_LENGTH, "recommendations.deptName");
			requireText(item.trackName(), MAX_NAME_LENGTH, "recommendations.trackName");
			if (item.label() != null && item.label().length() > MAX_LABEL_LENGTH) {
				throw invalid("recommendations.label");
			}
			if (isBlank(item.reason())) {
				throw invalid("recommendations.reason");
			}
			// 한 리포트 안에서 같은 대학·학과·전형은 한 번만 담는다. 구분자는 이름에 쓰이지 않는 제어 문자다.
			if (!targets.add(item.universityName() + '\u0000' + item.deptName() + '\u0000' + item.trackName())) {
				throw invalid("recommendations");
			}
		}
	}

	private void requireText(String value, int maxLength, String field) {
		if (isBlank(value) || value.length() > maxLength) {
			throw invalid(field);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private BusinessException invalid(String field) {
		return new BusinessException(ErrorCode.INVALID_REQUEST, "입력값이 올바르지 않습니다: " + field);
	}

}
