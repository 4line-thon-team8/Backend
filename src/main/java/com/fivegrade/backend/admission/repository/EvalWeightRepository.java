package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.EvalWeight;
import com.fivegrade.backend.admission.entity.EvalWeightId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalWeightRepository extends JpaRepository<EvalWeight, EvalWeightId> {

	List<EvalWeight> findByUnitYearId(Long unitYearId);

}
