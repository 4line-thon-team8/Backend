package com.fivegrade.backend.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * 희망 학과를 고르는 학과 사전. 기본키는 자동 증가 숫자가 아니라 학과 사전 코드다.
 * 대학마다 학과 이름이 달라서, 실제 모집단위와는 {@link UnitMajorMap} 으로 연결한다.
 */
@Entity
@Immutable
@Table(name = "majors")
public class Major {

	@Id
	@Column(name = "code", length = 30)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	protected Major() {
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

}
