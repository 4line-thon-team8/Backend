package com.fivegrade.backend.recommendation.candidate;

import com.fivegrade.backend.admission.entity.AdmissionUnit;
import com.fivegrade.backend.admission.entity.AdmissionUnitYear;
import com.fivegrade.backend.admission.repository.AdmissionUnitRepository;
import com.fivegrade.backend.admission.repository.AdmissionUnitYearRepository;
import com.fivegrade.backend.admission.repository.MajorTagRepository;
import com.fivegrade.backend.admission.repository.UnitMajorMapRepository;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.RecommendationQuery;
import com.fivegrade.backend.recommendation.dto.TrackPreference;
import com.fivegrade.backend.recommendation.probability.ProbabilityParameters;
import com.fivegrade.backend.recommendation.probability.TrackKind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 추천 후보가 될 모집단위를 전형 DB에서 읽어 온다.
 *
 * 후보는 경로별로 나뉜다. 우선순위는 희망 학과 → 비슷한 학과 → 인접 전형 → 자율전공이며,
 * 한 모집단위가 여러 경로에 걸리면 앞선 경로로 분류한다.
 * - 희망 학과: unit_major_map 으로 희망 학과 코드에 연결된 모집단위. 선호 전형과 같은 전형만.
 * - 비슷한 학과: 희망 학과와 "분야" 태그를 공유하는 다른 학과의 모집단위 (allowSimilarMajors 일 때만)
 * - 인접 전형: 위 두 경로 중 선호 전형이 아닌 전형의 모집단위
 * - 자율전공: 전공 미정(UNDECLARED) 학과에 연결된 모집단위
 *
 * 다음 모집단위는 후보에서 뺀다.
 * - 지원 자격이 제한된 전형 ({@link EligibilityRestrictions})
 * - 지원할 입시 연도(적재된 가장 최근 연도)의 모집 인원이 없거나 0인 모집단위. 모집하지 않는 곳은 추천할 수 없다.
 *
 * 사용자의 성적과 희망 학과 값은 로그나 예외 메시지에 남기지 않는다.
 */
@Component
public class RecommendationCandidateLoader {

	/** 비슷한 학과를 찾을 때 쓰는 태그 분류 */
	static final String SIMILARITY_TAG_CATEGORY = "분야";

	/** 학과를 정하지 않고 뽑는 모집단위가 연결된 학과 사전 코드 */
	static final String UNDECLARED_MAJOR_CODE = "UNDECLARED";

	private final UnitMajorMapRepository unitMajorMapRepository;
	private final MajorTagRepository majorTagRepository;
	private final AdmissionUnitRepository admissionUnitRepository;
	private final AdmissionUnitYearRepository admissionUnitYearRepository;

	public RecommendationCandidateLoader(
			UnitMajorMapRepository unitMajorMapRepository,
			MajorTagRepository majorTagRepository,
			AdmissionUnitRepository admissionUnitRepository,
			AdmissionUnitYearRepository admissionUnitYearRepository
	) {
		this.unitMajorMapRepository = unitMajorMapRepository;
		this.majorTagRepository = majorTagRepository;
		this.admissionUnitRepository = admissionUnitRepository;
		this.admissionUnitYearRepository = admissionUnitYearRepository;
	}

	/** 컷 예측에 쓸 최근 연도. 50%컷과 70%컷이 모두 적재된 연도를 최신부터 최대 3개. */
	public List<Integer> recentCutYears() {
		List<Integer> years = admissionUnitYearRepository.findCutYearsDescending();
		int usable = Math.min(years.size(), ProbabilityParameters.YEAR_WEIGHTS.length);
		return List.copyOf(years.subList(0, usable));
	}

	public List<CandidateUnit> load(RecommendationQuery query) {
		Set<Long> desiredIds = new LinkedHashSet<>(
				unitMajorMapRepository.findUnitIdsByMajorCodes(query.desiredMajorCodes()));

		Set<Long> similarIds = new LinkedHashSet<>();
		if (query.allowSimilarMajors()) {
			List<String> similarCodes = majorTagRepository
					.findSimilarMajorCodes(query.desiredMajorCodes(), SIMILARITY_TAG_CATEGORY).stream()
					.filter(code -> !UNDECLARED_MAJOR_CODE.equals(code))
					.toList();
			if (!similarCodes.isEmpty()) {
				similarIds.addAll(unitMajorMapRepository.findUnitIdsByMajorCodes(similarCodes));
				similarIds.removeAll(desiredIds);
			}
		}

		Set<Long> undeclaredIds = new LinkedHashSet<>(
				unitMajorMapRepository.findUnitIdsByMajorCodes(List.of(UNDECLARED_MAJOR_CODE)));
		undeclaredIds.removeAll(desiredIds);
		undeclaredIds.removeAll(similarIds);

		Set<Long> allIds = new LinkedHashSet<>(desiredIds);
		allIds.addAll(similarIds);
		allIds.addAll(undeclaredIds);
		if (allIds.isEmpty()) {
			return List.of();
		}

		Map<Long, List<UnitYearData>> yearsByUnit = loadYears(allIds);
		Integer latestYear = admissionUnitYearRepository.findLatestYear().orElse(null);

		List<CandidateUnit> candidates = new ArrayList<>();
		for (AdmissionUnit unit : admissionUnitRepository.findAllWithUniversityByIdIn(allIds)) {
			if (EligibilityRestrictions.isRestricted(
					unit.getUniversity().getName(), unit.getDeptName(), unit.getTrackName())) {
				continue;
			}
			List<UnitYearData> years = yearsByUnit.getOrDefault(unit.getId(), List.of());
			if (!isRecruiting(years, latestYear)) {
				continue;
			}
			CandidateKind kind = kindOf(unit, query.trackPreference(), desiredIds, similarIds);
			candidates.add(new CandidateUnit(
					unit.getId(),
					unit.getUniversity().getId(),
					unit.getUniversity().getName(),
					unit.getDeptName(),
					unit.getTrackType(),
					unit.getTrackName(),
					kind,
					years
			));
		}
		return candidates;
	}

	private Map<Long, List<UnitYearData>> loadYears(Set<Long> unitIds) {
		Map<Long, List<UnitYearData>> yearsByUnit = new HashMap<>();
		for (AdmissionUnitYear year : admissionUnitYearRepository.findAllByUnitIdIn(unitIds)) {
			yearsByUnit.computeIfAbsent(year.getUnit().getId(), id -> new ArrayList<>())
					.add(new UnitYearData(
							year.getYear(),
							year.getQuota(),
							toDouble(year.getCompetitionRate()),
							year.getNewOrMerged(),
							toDouble(year.getCutGrade50()),
							toDouble(year.getCutGrade70())
					));
		}
		return yearsByUnit;
	}

	/** 지원할 입시 연도에 모집 인원이 있는 모집단위인지 */
	private boolean isRecruiting(List<UnitYearData> years, Integer latestYear) {
		if (latestYear == null) {
			return false;
		}
		return years.stream()
				.anyMatch(year -> year.year() == latestYear && year.quota() != null && year.quota() > 0);
	}

	private CandidateKind kindOf(
			AdmissionUnit unit,
			TrackPreference preference,
			Set<Long> desiredIds,
			Set<Long> similarIds
	) {
		boolean preferredTrack = matches(preference, TrackKind.fromLabel(unit.getTrackType()));
		if (desiredIds.contains(unit.getId())) {
			return preferredTrack ? CandidateKind.DESIRED_MAJOR : CandidateKind.OTHER_TRACK;
		}
		if (similarIds.contains(unit.getId())) {
			return preferredTrack ? CandidateKind.SIMILAR_MAJOR : CandidateKind.OTHER_TRACK;
		}
		return CandidateKind.UNDECLARED;
	}

	/** 선호 전형에 해당하는 전형 계열인지. 상관없음이면 모두 해당한다. */
	private boolean matches(TrackPreference preference, TrackKind track) {
		return switch (preference) {
			case ANY -> true;
			case SUBJECT -> track == TrackKind.SUBJECT;
			case HOLISTIC -> track == TrackKind.HOLISTIC;
		};
	}

	private static Double toDouble(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}

}
