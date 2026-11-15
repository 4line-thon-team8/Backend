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
import org.hibernate.annotations.Immutable;

/**
 * 모집단위(대학 + 학과 + 전형). 연도와 무관한 정체성만 가진다.
 * 연도별 모집 인원과 입결은 {@link AdmissionUnitYear} 에 있다.
 */
@Entity
@Immutable
@Table(name = "admission_units")
public class AdmissionUnit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "university_id", nullable = false)
	private University university;

	@Column(name = "dept_name", nullable = false, length = 100)
	private String deptName;

	/** 교과, 학종 등 전형 유형. 값 목록은 아직 확정 전이라 문자열로 둔다. */
	@Column(name = "track_type", nullable = false, length = 20)
	private String trackType;

	@Column(name = "track_name", nullable = false, length = 100)
	private String trackName;

	protected AdmissionUnit() {
	}

	public Long getId() {
		return id;
	}

	public University getUniversity() {
		return university;
	}

	public String getDeptName() {
		return deptName;
	}

	public String getTrackType() {
		return trackType;
	}

	public String getTrackName() {
		return trackName;
	}

}
