package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.UnitMajorMap;
import com.fivegrade.backend.admission.entity.UnitMajorMapId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitMajorMapRepository extends JpaRepository<UnitMajorMap, UnitMajorMapId> {

	List<UnitMajorMap> findByMajorCode(String majorCode);

	List<UnitMajorMap> findByUnitId(Long unitId);

	/** 학과 코드들에 연결된 모집단위 ID. 여러 학과에 걸친 모집단위는 한 번만 나온다. */
	@Query("select distinct m.unit.id from UnitMajorMap m where m.major.code in :majorCodes")
	List<Long> findUnitIdsByMajorCodes(@Param("majorCodes") Collection<String> majorCodes);

}
