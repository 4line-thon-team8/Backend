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
 * 전공 키워드와 태그의 연결. 키워드 하나가 여러 태그에 걸릴 수 있다.
 * 마이그레이션으로만 바뀌는 읽기 전용 사전이다.
 */
@Entity
@Immutable
@Table(name = "tag_keywords")
public class TagKeyword {

	@EmbeddedId
	private TagKeywordId id;

	@MapsId("tagId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tag_id", nullable = false)
	private Tag tag;

	protected TagKeyword() {
	}

	public TagKeywordId getId() {
		return id;
	}

	public Tag getTag() {
		return tag;
	}

}
