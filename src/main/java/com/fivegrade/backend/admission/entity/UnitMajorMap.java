package com.fivegrade.backend.admission.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * 학과 사전({@link Major})과 대학별 모집단위({@link AdmissionUnit})의 N:M 연결.
 * 사용자가 고른 학과 코드로 실제 지원 가능한 모집단위를 찾을 때 쓴다.
 */
@Entity
@Immutable
@Table(name = "unit_major_map")
public class UnitMajorMap {

	@EmbeddedId
	private UnitMajorMapId id;

	@MapsId("unitId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "unit_id", nullable = false)
	private AdmissionUnit unit;

	@MapsId("majorCode")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "major_code", nullable = false)
	private Major major;

	protected UnitMajorMap() {
	}

	public UnitMajorMapId getId() {
		return id;
	}

	public AdmissionUnit getUnit() {
		return unit;
	}

	public Major getMajor() {
		return major;
	}

}
