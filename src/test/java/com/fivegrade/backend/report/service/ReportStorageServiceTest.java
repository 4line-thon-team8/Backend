package com.fivegrade.backend.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fivegrade.backend.TestcontainersConfiguration;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.report.dto.RecommendationDetail;
import com.fivegrade.backend.report.dto.RecommendationDraft;
import com.fivegrade.backend.report.dto.ReportDetail;
import com.fivegrade.backend.report.dto.ReportDraft;
import com.fivegrade.backend.report.dto.ReportSummary;
import com.fivegrade.backend.report.dto.SavedReport;
import com.fivegrade.backend.user.entity.User;
import com.fivegrade.backend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포트 저장·조회·삭제를 실제 PostgreSQL에서 확인한다.
 * 동시 요청과 별도 트랜잭션 동작을 보려고 테스트를 하나의 트랜잭션으로 감싸지 않는다.
 * 대신 테스트가 만든 사용자와 리포트는 끝날 때 직접 지운다.
 * 모든 값은 테스트 전용 가상 값이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReportStorageServiceTest {

	/** 오류 응답에 입력값이 노출되지 않는지 확인하기 위한 표식 */
	private static final String SECRET_REASON = "secret-reason-123";

	@Autowired
	private ReportStorageService service;

	@Autowired
	private UserRepository users;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<Long> createdUserIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (Long userId : createdUserIds) {
			service.deleteAllByUser(userId);
			users.deleteById(userId);
		}
		createdUserIds.clear();
	}

	@Test
	void 저장한_리포트를_그대로_조회한다() {
		Long userId = newUser();

		SavedReport saved = service.save(userId, "key-1", draft("안녕하세요", 3));
		ReportDetail detail = service.get(userId, saved.id());

		assertThat(saved.created()).isTrue();
		assertThat(detail.id()).isEqualTo(saved.id());
		assertThat(detail.createdAt()).isNotNull();
		assertThat(detail.greetingSummary()).isEqualTo("안녕하세요");
		assertThat(detail.recommendations()).hasSize(3);
		assertThat(detail.recommendations()).extracting(RecommendationDetail::rank).containsExactly(1, 2, 3);
		assertThat(detail.recommendations().get(0).universityName()).isEqualTo("대학1");
		assertThat(detail.recommendations().get(0).label()).isEqualTo("SAFE");
	}

	@Test
	void 추천이_6장보다_적어도_저장된다() {
		Long userId = newUser();

		SavedReport saved = service.save(userId, "key-1", draft(null, 1));

		assertThat(service.get(userId, saved.id()).recommendations()).hasSize(1);
	}

	@Test
	void 같은_키로_다시_저장하면_먼저_저장된_리포트를_돌려준다() {
		Long userId = newUser();

		SavedReport first = service.save(userId, "key-1", draft("첫 번째", 2));
		SavedReport second = service.save(userId, "key-1", draft("두 번째", 2));

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(second.created()).isFalse();
		assertThat(service.list(userId)).hasSize(1);
		assertThat(service.get(userId, first.id()).greetingSummary()).isEqualTo("첫 번째");
	}

	@Test
	void 다른_키로_저장하면_새_리포트가_만들어진다() {
		Long userId = newUser();

		SavedReport first = service.save(userId, "key-1", draft("a", 2));
		SavedReport second = service.save(userId, "key-2", draft("a", 2));

		assertThat(second.id()).isNotEqualTo(first.id());
		assertThat(service.list(userId)).hasSize(2);
	}

	@Test
	void 같은_키가_동시에_들어와도_한_건만_저장된다() throws Exception {
		Long userId = newUser();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<SavedReport> task = () -> {
			start.await();
			return service.save(userId, "same-key", draft("동시", 2));
		};

		try {
			Future<SavedReport> a = pool.submit(task);
			Future<SavedReport> b = pool.submit(task);
			start.countDown();
			SavedReport first = a.get();
			SavedReport second = b.get();

			assertThat(first.id()).isEqualTo(second.id());
			assertThat(List.of(first.created(), second.created())).containsOnlyOnce(true);
			assertThat(service.list(userId)).hasSize(1);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void 잘못된_입력은_INVALID_REQUEST이고_입력값은_메시지에_없다() {
		Long userId = newUser();
		List<ReportDraft> invalid = List.of(
				new ReportDraft(null, List.of()),
				new ReportDraft(null, null),
				draft(null, 7),
				new ReportDraft(null, List.of(item(1, "대학1", "학과1", "전형1", SECRET_REASON),
						item(1, "대학2", "학과2", "전형2", SECRET_REASON))),
				new ReportDraft(null, List.of(item(0, "대학1", "학과1", "전형1", SECRET_REASON))),
				new ReportDraft(null, List.of(item(7, "대학1", "학과1", "전형1", SECRET_REASON))),
				new ReportDraft(null, List.of(item(1, "대학1", "학과1", "전형1", SECRET_REASON),
						item(2, "대학1", "학과1", "전형1", SECRET_REASON))),
				new ReportDraft(null, List.of(item(1, "대학1", "학과1", "전형1", " "))));

		for (ReportDraft draft : invalid) {
			assertThatThrownBy(() -> service.save(userId, "key-x", draft))
					.isInstanceOfSatisfying(BusinessException.class, e -> {
						assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
						assertThat(e.getMessage()).doesNotContain(SECRET_REASON);
					});
		}
		assertThat(service.list(userId)).isEmpty();
	}

	@Test
	void 멱등성_키가_비어_있으면_INVALID_REQUEST() {
		Long userId = newUser();

		assertThatThrownBy(() -> service.save(userId, " ", draft(null, 1)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
	}

	@Test
	void 남의_리포트는_403_없는_리포트는_404() {
		Long owner = newUser();
		Long other = newUser();
		SavedReport saved = service.save(owner, "key-1", draft(null, 1));

		assertThatThrownBy(() -> service.get(other, saved.id()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
		assertThatThrownBy(() -> service.get(owner, UUID.randomUUID()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NOT_FOUND));
	}

	@Test
	void 목록은_본인_것만_최신순이다() throws Exception {
		Long userId = newUser();
		Long other = newUser();
		SavedReport older = service.save(userId, "key-1", draft("오래된", 1));
		Thread.sleep(20);
		SavedReport newer = service.save(userId, "key-2", draft("최신", 1));
		service.save(other, "key-3", draft("남의 것", 1));

		List<ReportSummary> list = service.list(userId);

		assertThat(list).extracting(ReportSummary::id).containsExactly(newer.id(), older.id());
	}

	@Test
	void 삭제하면_추천_원서도_함께_지워진다() {
		Long userId = newUser();
		SavedReport saved = service.save(userId, "key-1", draft(null, 3));

		service.delete(userId, saved.id());

		assertThat(countRecommendations(saved.id())).isZero();
		assertThatThrownBy(() -> service.get(userId, saved.id()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NOT_FOUND));
	}

	@Test
	void 남의_리포트는_삭제할_수_없고_없는_리포트는_404() {
		Long owner = newUser();
		Long other = newUser();
		SavedReport saved = service.save(owner, "key-1", draft(null, 1));

		assertThatThrownBy(() -> service.delete(other, saved.id()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
		assertThatThrownBy(() -> service.delete(owner, UUID.randomUUID()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_NOT_FOUND));
		assertThat(service.get(owner, saved.id())).isNotNull();
	}

	@Test
	void 탈퇴용_삭제는_해당_사용자의_리포트만_지운다() {
		Long leaving = newUser();
		Long staying = newUser();
		SavedReport gone = service.save(leaving, "key-1", draft(null, 2));
		service.save(leaving, "key-2", draft(null, 2));
		SavedReport kept = service.save(staying, "key-3", draft(null, 2));

		long deleted = service.deleteAllByUser(leaving);

		assertThat(deleted).isEqualTo(2);
		assertThat(service.list(leaving)).isEmpty();
		assertThat(countRecommendations(gone.id())).isZero();
		assertThat(service.get(staying, kept.id()).recommendations()).hasSize(2);
	}

	@Test
	void 탈퇴가_사용자_행을_잠그고_있으면_저장은_기다렸다가_탈퇴_뒤에는_실패하고_리포트가_남지_않는다() throws Exception {
		// 탈퇴는 사용자 행을 먼저 잠근 뒤(SELECT ... FOR UPDATE) 리포트와 계정을 지운다.
		// 리포트 저장은 users 를 참조하므로 그 행에 키 공유 잠금을 요구하고, 두 잠금은 서로를 기다리게 한다.
		// 그래서 따로 잠금을 두지 않아도 저장과 탈퇴가 겹치지 않는다는 것을 확인한다.
		Long userId = newUser();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch finishWithdrawal = new CountDownLatch(1);
		TransactionTemplate withdrawal = new TransactionTemplate(transactionManager);

		try {
			Future<?> withdrawing = pool.submit(() -> withdrawal.executeWithoutResult(status -> {
				jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
				locked.countDown();
				awaitOrFail(finishWithdrawal);
				jdbc.update("DELETE FROM reports WHERE user_id = ?", userId);
				jdbc.update("DELETE FROM users WHERE id = ?", userId);
			}));
			awaitOrFail(locked);

			Future<SavedReport> saving = pool.submit(() -> service.save(userId, "withdraw-key", draft(null, 1)));
			Thread.sleep(500);

			// 탈퇴가 사용자 행을 잠근 동안 저장은 끝나지 않고 기다린다.
			assertThat(saving).isNotDone();

			finishWithdrawal.countDown();
			withdrawing.get(10, TimeUnit.SECONDS);

			// 사용자가 이미 없으므로 저장은 외래 키 위반으로 실패하고, 탈퇴한 사용자의 리포트는 남지 않는다.
			assertThatThrownBy(() -> saving.get(10, TimeUnit.SECONDS))
					.isInstanceOf(ExecutionException.class)
					.hasCauseInstanceOf(DataIntegrityViolationException.class);
			Long remaining = jdbc.queryForObject("SELECT count(*) FROM reports WHERE user_id = ?", Long.class, userId);
			assertThat(remaining).isZero();
		} finally {
			finishWithdrawal.countDown();
			pool.shutdownNow();
		}
	}

	private static void awaitOrFail(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("시간 안에 신호가 오지 않았습니다.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private Long newUser() {
		User user = users.save(new User("report-test-" + UUID.randomUUID() + "@example.com", "test-only-encoded-password"));
		createdUserIds.add(user.getId());
		return user.getId();
	}

	private long countRecommendations(UUID reportId) {
		Long count = jdbc.queryForObject(
				"SELECT count(*) FROM report_recommendations WHERE report_id = ?", Long.class, reportId);
		return count == null ? 0 : count;
	}

	/** 대학1·학과1·전형1 … 처럼 서로 다른 추천 원서 count장을 만든다. */
	private static ReportDraft draft(String greetingSummary, int count) {
		List<RecommendationDraft> items = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			items.add(new RecommendationDraft(i, "대학" + i, "학과" + i, "전형" + i, "SAFE", "추천 이유 " + i, null));
		}
		return new ReportDraft(greetingSummary, items);
	}

	private static RecommendationDraft item(int rank, String university, String dept, String track, String reason) {
		return new RecommendationDraft(rank, university, dept, track, "MATCH", reason, null);
	}

}
