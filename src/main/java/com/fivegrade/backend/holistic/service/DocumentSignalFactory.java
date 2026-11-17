package com.fivegrade.backend.holistic.service;

import com.fivegrade.backend.holistic.profile.HolisticTrackProfile;
import com.fivegrade.backend.holistic.profile.HolisticTrackProfileRepository;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator;
import com.fivegrade.backend.holistic.scoring.DocumentSignal;
import com.fivegrade.backend.recommendation.candidate.CandidateUnit;
import com.fivegrade.backend.recommendation.probability.TrackKind;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 후보 모집단위마다 적용할 서류 입력(그 전형 기준의 서류 수준과 서류 비중)을 만든다.
 *
 * 후보와 서류 평가 프로필은 (대학, 전형 이름)으로 연결하고, 같은 전형의 여러 연도 행 중 가장 최근 연도를 쓴다.
 * 학종 전형만 서류 입력을 만든다. 교과 전형의 서류 적합도는 별도 판단이라 여기서는 다루지 않는다.
 * 프로필이 없거나 서류 수준을 만들 수 없는 후보는 null 이다. 그 후보에는 서류 보정을 하지 않는다.
 */
@Component
public class DocumentSignalFactory {

	private final Function<Collection<Long>, List<HolisticTrackProfile>> profileSource;
	private final DocumentLevelCalculator levelCalculator = new DocumentLevelCalculator();

	@Autowired
	public DocumentSignalFactory(HolisticTrackProfileRepository repository) {
		this(repository::findByUniversityIdInOrderByAdmissionYearDesc);
	}

	/** 테스트에서 프로필 조회를 대신하기 위한 진입점이다. 최신 연도가 앞에 오는 순서로 돌려줘야 한다. */
	DocumentSignalFactory(Function<Collection<Long>, List<HolisticTrackProfile>> profileSource) {
		this.profileSource = profileSource;
	}

	/** 후보 목록을 받아 후보 → 서류 입력 함수를 만든다. 프로필은 한 번에 읽는다. */
	public Function<CandidateUnit, DocumentSignal> resolverFor(
			List<CandidateUnit> units,
			DocumentLevelCalculator.ElementLevels levels
	) {
		Set<Long> universityIds = units.stream()
				.filter(unit -> TrackKind.fromLabel(unit.trackType()) == TrackKind.HOLISTIC)
				.map(CandidateUnit::universityId)
				.collect(Collectors.toSet());
		if (universityIds.isEmpty()) {
			return unit -> null;
		}

		Map<String, HolisticTrackProfile> latest = new HashMap<>();
		for (HolisticTrackProfile profile : profileSource.apply(universityIds)) {
			if (TrackKind.fromLabel(profile.getTrackKind()) == TrackKind.HOLISTIC) {
				latest.putIfAbsent(key(profile.getUniversityId(), profile.getTrackName()), profile);
			}
		}
		return unit -> signalOf(latest.get(key(unit.universityId(), unit.trackName())), unit, levels);
	}

	private DocumentSignal signalOf(
			HolisticTrackProfile profile,
			CandidateUnit unit,
			DocumentLevelCalculator.ElementLevels levels
	) {
		if (TrackKind.fromLabel(unit.trackType()) != TrackKind.HOLISTIC) {
			return null;
		}
		if (profile == null) {
			return displayOnlySignal(levels);
		}
		DocumentLevelCalculator.AxisWeights weights = new DocumentLevelCalculator.AxisWeights(
				percent(profile.getAcademicRatio()), percent(profile.getCareerRatio()),
				percent(profile.getCommunityRatio()));
		Optional<Double> level = levelCalculator.compute(levels, weights);
		if (level.isEmpty()) {
			return null;
		}
		BigDecimal share = profile.getDocumentShare();
		return new DocumentSignal(level.get(), share == null ? null : share.doubleValue() / 100.0,
				basisOf(levels, weights));
	}

	/**
	 * 전형 프로필이 없는 학종 후보. 전형 비중을 모르므로 보정하지 않고, 세 축을 같은 비중으로 본 서류 수준을
	 * 적합도 표시용으로만 붙인다. 프로필 적재가 끝나지 않은 대학도 서류 적합도 안내를 받을 수 있게 한다.
	 */
	private DocumentSignal displayOnlySignal(DocumentLevelCalculator.ElementLevels levels) {
		DocumentLevelCalculator.AxisWeights even = new DocumentLevelCalculator.AxisWeights(1.0, 1.0, 1.0);
		return levelCalculator.compute(levels, even)
				.map(level -> DocumentSignal.displayOnly(level, basisOf(levels, even)))
				.orElse(null);
	}

	/** 전형 비중 x |요소 수준 - 3(중립)|이 큰 요소 최대 2개. 중립이면 근거로 삼지 않는다. */
	private static List<String> basisOf(
			DocumentLevelCalculator.ElementLevels levels,
			DocumentLevelCalculator.AxisWeights weights
	) {
		Map<String, Double> contribution = new java.util.LinkedHashMap<>();
		put(contribution, "ACADEMIC", levels.academic(), weights.academic());
		put(contribution, "MAJOR_FIT", levels.career(), weights.career());
		put(contribution, "COMMUNITY", levels.community(), weights.community());
		return contribution.entrySet().stream()
				.filter(entry -> entry.getValue() > 0)
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(2)
				.map(Map.Entry::getKey)
				.toList();
	}

	private static void put(Map<String, Double> target, String axis, Double level, Double weight) {
		if (level != null && weight != null && weight > 0) {
			target.put(axis, weight * Math.abs(level - 3.0));
		}
	}

	private static Double percent(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}

	private static String key(Long universityId, String trackName) {
		return universityId + "\u0000" + trackName;
	}

}
