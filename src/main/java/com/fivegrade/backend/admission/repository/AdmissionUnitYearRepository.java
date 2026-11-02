package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.AdmissionUnitYear;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionUnitYearRepository extends JpaRepository<AdmissionUnitYear, Long> {

	List<AdmissionUnitYear> findByUnitId(Long unitId);

	Optional<AdmissionUnitYear> findByUnitIdAndYear(Long unitId, Integer year);

}
