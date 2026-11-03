package com.fivegrade.backend.admission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * eval_weights 의 복합 기본키 (연도별 모집단위 + 평가요소).
 */
@Embeddable
public record EvalWeightId(

		@Column(name = "unit_year_id") Long unitYearId,

		@Column(name = "element", length = 30) String element

) implements Serializable {
}
