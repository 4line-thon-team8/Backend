package com.fivegrade.backend.admission.service;

import com.fivegrade.backend.admission.dto.TrackResponse;
import com.fivegrade.backend.admission.dto.UniversityListResponse;
import com.fivegrade.backend.admission.dto.UniversityResponse;
import com.fivegrade.backend.admission.dto.UniversityTrackRow;
import com.fivegrade.backend.admission.repository.AdmissionUnitRepository;
import com.fivegrade.backend.recommendation.candidate.EligibilityRestrictions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대학·전형 목록 조회. 적재된 모집단위에서 대학별 전형을 모아 돌려준다.
 *
 * 농어촌·기회균형처럼 대상자만 지원할 수 있는 전형은 추천과 같은 기준(EligibilityRestrictions)으로 뺀다.
 * 목록에 있는 전형은 일반 학생이 지원할 수 있는 전형이라는 뜻이다.
 */
@Service
public class UniversityService {

	private static final Comparator<TrackResponse> BY_TYPE_THEN_NAME =
			Comparator.comparing(TrackResponse::type).thenComparing(TrackResponse::name);

	private final AdmissionUnitRepository admissionUnitRepository;

	public UniversityService(AdmissionUnitRepository admissionUnitRepository) {
		this.admissionUnitRepository = admissionUnitRepository;
	}

	@Transactional(readOnly = true)
	public UniversityListResponse findAll() {
		// 대학 이름순으로 정렬해 두고, 같은 전형이 여러 번 들어와도 한 번만 담는다.
		Map<String, Set<TrackResponse>> tracksByUniversity = new TreeMap<>();
		for (UniversityTrackRow row : admissionUnitRepository.findDistinctTracks()) {
			if (EligibilityRestrictions.isRestricted(row.trackName())) {
				continue;
			}
			tracksByUniversity
					.computeIfAbsent(row.universityName(), name -> new LinkedHashSet<>())
					.add(new TrackResponse(row.trackType(), row.trackName()));
		}

		List<UniversityResponse> universities = new ArrayList<>();
		for (Map.Entry<String, Set<TrackResponse>> entry : tracksByUniversity.entrySet()) {
			List<TrackResponse> tracks = entry.getValue().stream().sorted(BY_TYPE_THEN_NAME).toList();
			universities.add(new UniversityResponse(entry.getKey(), tracks));
		}
		return new UniversityListResponse(universities);
	}

}
