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
 * 연도별 모집단위의 평가요소 반영 비율. 예: 교과 70, 서류 30.
 */
@Entity
@Immutable
@Table(name = "eval_weights")
public class EvalWeight {

	@EmbeddedId
	private EvalWeightId id;

	@MapsId("unitYearId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "unit_year_id", nullable = false)
	private AdmissionUnitYear unitYear;

	/** 반영 비율(%). 0 이상 100 이하. */
	@Column(name = "ratio", nullable = false, precision = 5, scale = 2)
	private BigDecimal ratio;

	protected EvalWeight() {
	}

	public EvalWeightId getId() {
		return id;
	}

	public AdmissionUnitYear getUnitYear() {
		return unitYear;
	}

	public String getElement() {
		return id.element();
	}

	public BigDecimal getRatio() {
		return ratio;
	}

}
