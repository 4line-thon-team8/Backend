package com.fivegrade.backend.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/**
 * 모집단위의 연도별 정보(모집 인원, 지원 인원, 경쟁률, 입결).
 *
 * 입결 통계는 학교가 공개한 것만 채우므로 통계 컬럼은 모두 null 일 수 있다.
 * 등급 값은 모두 9등급 원본이며, 5등급 환산은 환산 엔진이 따로 한다.
 */
@Entity
@Immutable
@Table(name = "admission_unit_years")
public class AdmissionUnitYear {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "unit_id", nullable = false)
	private AdmissionUnit unit;

	@Column(name = "year", nullable = false)
	private Integer year;

	@Column(name = "quota")
	private Integer quota;

	@Column(name = "applicants")
	private Integer applicants;

	@Column(name = "competition_rate", precision = 8, scale = 2)
	private BigDecimal competitionRate;

	/** 수능 최저학력기준 원문. */
	@Column(name = "csat_min")
	private String csatMin;

	@Column(name = "is_new_or_merged")
	private Boolean newOrMerged;

	@Column(name = "grade_avg", precision = 4, scale = 2)
	private BigDecimal gradeAvg;

	/** 입결 '최저'. 가장 낮은 성적, 즉 등급 숫자가 가장 큰 값이다. */
	@Column(name = "grade_min", precision = 4, scale = 2)
	private BigDecimal gradeMin;

	// 컬럼 이름에 숫자가 끼어 있어(cut_grade_50) 기본 이름 변환(cut_grade50)과 달라지므로 name 을 명시한다.
	@Column(name = "cut_grade_50", precision = 4, scale = 2)
	private BigDecimal cutGrade50;

	@Column(name = "cut_grade_70", precision = 4, scale = 2)
	private BigDecimal cutGrade70;

	/** 충원율(%). 100 이 100% 이며 100 을 넘을 수 있다. */
	@Column(name = "fill_rate", precision = 8, scale = 1)
	private BigDecimal fillRate;

	/** 충원합격 예비번호. */
	@Column(name = "fill_rank")
	private Integer fillRank;

	/** 산출 기준 등 자유 기재. */
	@Column(name = "note")
	private String note;

	@Column(name = "source", length = 300)
	private String source;

	@Column(name = "collected_at")
	private LocalDate collectedAt;

	protected AdmissionUnitYear() {
	}

	public Long getId() {
		return id;
	}

	public AdmissionUnit getUnit() {
		return unit;
	}

	public Integer getYear() {
		return year;
	}

	public Integer getQuota() {
		return quota;
	}

	public Integer getApplicants() {
		return applicants;
	}

	public BigDecimal getCompetitionRate() {
		return competitionRate;
	}

	public String getCsatMin() {
		return csatMin;
	}

	public Boolean getNewOrMerged() {
		return newOrMerged;
	}

	public BigDecimal getGradeAvg() {
		return gradeAvg;
	}

	public BigDecimal getGradeMin() {
		return gradeMin;
	}

	public BigDecimal getCutGrade50() {
		return cutGrade50;
	}

	public BigDecimal getCutGrade70() {
		return cutGrade70;
	}

	public BigDecimal getFillRate() {
		return fillRate;
	}

	public Integer getFillRank() {
		return fillRank;
	}

	public String getNote() {
		return note;
	}

	public String getSource() {
		return source;
	}

	public LocalDate getCollectedAt() {
		return collectedAt;
	}

}
