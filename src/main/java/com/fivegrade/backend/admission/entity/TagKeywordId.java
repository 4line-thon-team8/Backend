package com.fivegrade.backend.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * tag_keywords 의 복합 기본키 (키워드 + 태그).
 */
@Embeddable
public record TagKeywordId(

		@Column(name = "keyword", length = 50) String keyword,

		@Column(name = "tag_id") Long tagId

) implements Serializable {
}
