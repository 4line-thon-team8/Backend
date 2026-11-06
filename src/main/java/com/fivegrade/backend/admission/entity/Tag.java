package com.fivegrade.backend.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * 학과에 붙는 태그. 태그가 겹치는 정도로 비슷한 학과를 찾는다(인접 학과 확대).
 */
@Entity
@Immutable
@Table(name = "tags")
public class Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 50)
	private String name;

	/** 태그 분류. 선택값이라 null 일 수 있다. */
	@Column(name = "category", length = 30)
	private String category;

	protected Tag() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

}
