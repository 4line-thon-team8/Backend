package com.fivegrade.backend.holistic.profile;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 서류 평가 프로필 조회. 후보와의 연결은 (대학, 전형 이름)이며, 같은 전형의 여러 연도 행 중
 * 가장 최근 연도를 쓰는 일은 호출하는 서비스가 한다.
 */
public interface HolisticTrackProfileRepository extends JpaRepository<HolisticTrackProfile, Long> {

	List<HolisticTrackProfile> findByUniversityIdInOrderByAdmissionYearDesc(Collection<Long> universityIds);

}
