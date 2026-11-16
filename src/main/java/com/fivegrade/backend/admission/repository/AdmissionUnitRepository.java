package com.fivegrade.backend.admission.repository;

import com.fivegrade.backend.admission.dto.UniversityTrackRow;
import com.fivegrade.backend.admission.entity.AdmissionUnit;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionUnitRepository extends JpaRepository<AdmissionUnit, Long> {

	List<AdmissionUnit> findByUniversityId(Long universityId);

	/** 추천 후보 조회용. 대학 이름을 함께 읽어 모집단위마다 대학을 따로 조회하지 않게 한다. */
	@Query("select u from AdmissionUnit u join fetch u.university where u.id in :unitIds")
	List<AdmissionUnit> findAllWithUniversityByIdIn(@Param("unitIds") Collection<Long> unitIds);

	/** 대학별 전형 목록용. 학과마다 반복되는 전형을 대학·전형 유형·전형 이름 단위로 한 번씩만 꺼낸다. */
	@Query("select distinct new com.fivegrade.backend.admission.dto.UniversityTrackRow("
			+ "u.university.name, u.trackType, u.trackName) from AdmissionUnit u")
	List<UniversityTrackRow> findDistinctTracks();

}
