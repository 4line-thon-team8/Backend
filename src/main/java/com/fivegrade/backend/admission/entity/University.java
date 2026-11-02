package com.fivegrade.backend.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * 대학. 전형 데이터(seed)로만 채워지는 읽기 전용 엔티티다.
 */
@Entity
@Immutable
@Table(name = "universities")
public class University {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	protected University() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

}
