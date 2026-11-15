package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.entity.AdmissionUnitYear;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionUnitYearRepository extends JpaRepository<AdmissionUnitYear, Long> {

	List<AdmissionUnitYear> findByUnitId(Long unitId);

	Optional<AdmissionUnitYear> findByUnitIdAndYear(Long unitId, Integer year);

	/** 여러 모집단위의 연도별 정보를 한 번에 읽는다. 추천 후보 조회용. */
	@Query("select y from AdmissionUnitYear y join fetch y.unit u where u.id in :unitIds")
	List<AdmissionUnitYear> findAllByUnitIdIn(@Param("unitIds") Collection<Long> unitIds);

	/** 50%컷과 70%컷이 모두 있는 연도를 최신부터 나열한다. 컷 예측의 가중 연도를 정할 때 쓴다. */
	@Query("select distinct y.year from AdmissionUnitYear y"
			+ " where y.cutGrade50 is not null and y.cutGrade70 is not null"
			+ " order by y.year desc")
	List<Integer> findCutYearsDescending();

	/** 적재된 가장 최근 연도. 지원할 입시 연도의 모집 정보가 이 연도에 담긴다. */
	@Query("select max(y.year) from AdmissionUnitYear y")
	Optional<Integer> findLatestYear();

}
