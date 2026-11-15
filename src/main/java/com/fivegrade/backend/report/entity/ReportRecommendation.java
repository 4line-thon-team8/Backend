package com.fivegrade.backend.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * 리포트에 저장된 추천 원서 한 장.
 * 대학·학과·전형 이름은 저장 시점 값을 복사한 스냅샷이라, 전형 데이터가 바뀌어도 리포트는 그대로 남는다.
 * 성적과 환산 결과는 담지 않는다.
 */
@Entity
@Table(name = "report_recommendations")
public class ReportRecommendation {

	@EmbeddedId
	private ReportRecommendationId id;

	@MapsId("reportId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id")
	private Report report;

	@Column(name = "university_name", nullable = false, length = 100)
	private String universityName;

	@Column(name = "dept_name", nullable = false, length = 100)
	private String deptName;

	@Column(name = "track_name", nullable = false, length = 100)
	private String trackName;

	@Column(name = "label", length = 30)
	private String label;

	@Column(name = "reason", nullable = false)
	private String reason;

	/** 전형 데이터 참조용 선택값. 전형 데이터가 지워지면 DB가 NULL로 바꾼다. */
	@Column(name = "unit_id")
	private Long unitId;

	protected ReportRecommendation() {
	}

	ReportRecommendation(Report report, int rank, String universityName, String deptName,
			String trackName, String label, String reason, Long unitId) {
		this.report = report;
		this.id = new ReportRecommendationId(rank);
		this.universityName = universityName;
		this.deptName = deptName;
		this.trackName = trackName;
		this.label = label;
		this.reason = reason;
		this.unitId = unitId;
	}

	public int getRank() {
		return id.getRank();
	}

	public String getUniversityName() {
		return universityName;
	}

	public String getDeptName() {
		return deptName;
	}

	public String getTrackName() {
		return trackName;
	}

	public String getLabel() {
		return label;
	}

	public String getReason() {
		return reason;
	}

	public Long getUnitId() {
		return unitId;
	}

}
