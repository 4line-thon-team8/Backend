package com.fivegrade.backend.recommendation.candidate;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import java.util.List;

/**
 * 추천 후보가 된 모집단위와 그 연도별 정보.
 *
 * @param unitId          모집단위 ID
 * @param universityId    대학 ID. 대학당 카드 수 제한에 쓴다.
 * @param universityName  대학 이름
 * @param departmentName  학과(모집단위) 이름
 * @param trackType       전형 유형(교과, 학종)
 * @param trackName       전형 이름
 * @param kind            후보가 오른 경로
 * @param years           연도별 정보. 순서는 상관없다.
 */
public record CandidateUnit(
		long unitId,
		long universityId,
		String universityName,
		String departmentName,
		String trackType,
		String trackName,
		CandidateKind kind,
		List<UnitYearData> years
) {

	public CandidateUnit {
		years = List.copyOf(years);
	}

}
