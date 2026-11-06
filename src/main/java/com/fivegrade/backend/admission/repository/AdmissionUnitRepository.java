package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.AdmissionUnit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionUnitRepository extends JpaRepository<AdmissionUnit, Long> {

	List<AdmissionUnit> findByUniversityId(Long universityId);

}
