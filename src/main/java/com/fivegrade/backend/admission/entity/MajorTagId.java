package com.fivegrade.backend.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * major_tags 의 복합 기본키 (학과 코드 + 태그).
 */
@Embeddable
public record MajorTagId(

		@Column(name = "major_code", length = 30) String majorCode,

		@Column(name = "tag_id") Long tagId

) implements Serializable {
}
