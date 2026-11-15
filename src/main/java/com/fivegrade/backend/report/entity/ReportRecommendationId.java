package com.fivegrade.backend.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 추천 원서의 기본키. 한 리포트 안에서 지망 순서(rank)가 곧 식별자다.
 */
@Embeddable
public class ReportRecommendationId implements Serializable {

	@Column(name = "report_id", nullable = false)
	private UUID reportId;

	@Column(name = "rank", nullable = false)
	private int rank;

	protected ReportRecommendationId() {
	}

	ReportRecommendationId(int rank) {
		this.rank = rank;
	}

	public UUID getReportId() {
		return reportId;
	}

	public int getRank() {
		return rank;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ReportRecommendationId that)) {
			return false;
		}
		return rank == that.rank && Objects.equals(reportId, that.reportId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(reportId, rank);
	}

}
