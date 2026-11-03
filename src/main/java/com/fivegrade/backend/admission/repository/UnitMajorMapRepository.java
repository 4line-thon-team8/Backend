package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.UnitMajorMap;
import com.fivegrade.backend.admission.entity.UnitMajorMapId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitMajorMapRepository extends JpaRepository<UnitMajorMap, UnitMajorMapId> {

	List<UnitMajorMap> findByMajorCode(String majorCode);

	List<UnitMajorMap> findByUnitId(Long unitId);

}
