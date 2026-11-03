package com.fivegrade.backend.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * unit_major_map 의 복합 기본키 (모집단위 + 학과 코드).
 */
@Embeddable
public record UnitMajorMapId(

		@Column(name = "unit_id") Long unitId,

		@Column(name = "major_code", length = 30) String majorCode

) implements Serializable {
}
