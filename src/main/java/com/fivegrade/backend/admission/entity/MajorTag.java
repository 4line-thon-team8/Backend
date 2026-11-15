package com.fivegrade.backend.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/**
 * 학과와 태그의 연결. weight 는 학과와 태그의 연관 강도이며 선택값이다.
 */
@Entity
@Immutable
@Table(name = "major_tags")
public class MajorTag {

	@EmbeddedId
	private MajorTagId id;

	@MapsId("majorCode")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "major_code", nullable = false)
	private Major major;

	@MapsId("tagId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tag_id", nullable = false)
	private Tag tag;

	@Column(name = "weight", precision = 4, scale = 2)
	private BigDecimal weight;

	protected MajorTag() {
	}

	public MajorTagId getId() {
		return id;
	}

	public Major getMajor() {
		return major;
	}

	public Tag getTag() {
		return tag;
	}

	public BigDecimal getWeight() {
		return weight;
	}

}
