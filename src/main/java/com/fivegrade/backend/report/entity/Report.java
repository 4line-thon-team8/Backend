package com.fivegrade.backend.report.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 로그인 사용자가 저장한 리포트의 최소 항목.
 * 성적 숫자, 환산 결과, 요청 원본은 저장하지 않는다(명세서 P-04).
 * user_id 에는 DB 삭제 규칙이 없으므로, 탈퇴 시에는 서비스가 리포트를 먼저 지운다.
 * API 응답에는 이 엔티티를 직접 내보내지 않고 DTO 로 바꿔서 쓴다.
 */
@Entity
@Table(name = "reports")
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "greeting_summary")
	private String greetingSummary;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id.rank ASC")
	private List<ReportRecommendation> recommendations = new ArrayList<>();

	protected Report() {
	}

	public Report(Long userId, String idempotencyKey, String greetingSummary) {
		this.userId = userId;
		this.idempotencyKey = idempotencyKey;
		this.greetingSummary = greetingSummary;
		this.createdAt = Instant.now();
	}

	public void addRecommendation(int rank, String universityName, String deptName,
			String trackName, String label, String reason, Long unitId) {
		recommendations.add(new ReportRecommendation(
				this, rank, universityName, deptName, trackName, label, reason, unitId));
	}

	public UUID getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getGreetingSummary() {
		return greetingSummary;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public List<ReportRecommendation> getRecommendations() {
		return recommendations;
	}

}
