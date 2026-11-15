package com.fivegrade.backend.recommendation.selection;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 계산을 마친 후보에서 추천 원서 최대 6장을 고른다. (이슈 #31 "카드 구성 규칙", "6장을 채우지 못할 때")
 *
 * 목표는 안정 2 · 적정 2 · 소신 2 이고, 대학당 최대 2장, 같은 (대학, 학과)는 한 번만 담는다.
 * 후보는 경로별로 희망 학과 → 비슷한 학과 → 인접 전형 → 자율전공 순서로 쓴다.
 * 각 경로에서 다음 순서로 채우고, 6장이 차면 멈춘다.
 * 1. 구간별 2장씩 (안정 → 적정 → 소신)
 * 2. 부족하면 적정 → 안정 → 소신 순으로 같은 경로의 남은 후보
 * 모든 경로를 돌고도 부족하면 마지막으로 과잉(OVER) → 먼 카드(FAR) → 평가 불가(UNRATED) 후보를
 * 같은 경로 순서로 채운다. 이 카드들은 결과에서 안내(warnings)가 붙는다.
 *
 * 상향 카드를 요청했는데 소신 카드가 하나도 없고 소신 후보가 있으면, 덜 아까운 카드를 빼고 소신 카드를 넣는다. 같은 대학의 카드를 빼야 들어가는 경우도 시도한다.
 *
 * 구간 안에서 후보를 고르는 기준(구간별 목표 가능성에 가까운 순)과 최종 지망 순서(소신 → 적정 → 안정 → 보충)는
 * 기획이 확정하기 전의 임시 기준이다. 바꿀 때는 {@link #PREFERRED_PROBABILITY}와 {@link #RANK_ORDER}만 고친다.
 *
 * Spring과 DB에 의존하지 않는 순수 Java 클래스다.
 */
public final class CardSelector {

	/** 대학당 최대 카드 수 */
	static final int MAX_PER_UNIVERSITY = 2;

	/** 구간별 목표 카드 수 */
	static final int TARGET_PER_BAND = 2;

	private static final List<CandidateKind> STAGE_ORDER = List.of(
			CandidateKind.DESIRED_MAJOR,
			CandidateKind.SIMILAR_MAJOR,
			CandidateKind.OTHER_TRACK,
			CandidateKind.UNDECLARED
	);

	private static final List<AdmissionBand> TARGET_ORDER = List.of(
			AdmissionBand.SAFE, AdmissionBand.MATCH, AdmissionBand.REACH);

	private static final List<AdmissionBand> FILL_ORDER = List.of(
			AdmissionBand.MATCH, AdmissionBand.SAFE, AdmissionBand.REACH);

	private static final List<AdmissionBand> LAST_RESORT_ORDER = List.of(
			AdmissionBand.OVER, AdmissionBand.FAR, AdmissionBand.UNRATED);

	/** 최종 지망 순서. 도전적인 카드부터 놓고 보충 카드는 뒤로 보낸다. */
	static final List<AdmissionBand> RANK_ORDER = List.of(
			AdmissionBand.REACH, AdmissionBand.MATCH, AdmissionBand.SAFE,
			AdmissionBand.OVER, AdmissionBand.FAR, AdmissionBand.UNRATED);

	/** 구간 안에서 이 가능성에 가까운 후보를 먼저 고른다. 구간 범위의 가운데쯤이다. */
	static final Map<AdmissionBand, Double> PREFERRED_PROBABILITY = Map.of(
			AdmissionBand.SAFE, 0.90,
			AdmissionBand.MATCH, 0.65,
			AdmissionBand.REACH, 0.25
	);

	/**
	 * @param candidates       구간 계산을 마친 후보. 같은 모집단위가 두 번 들어 있으면 안 된다.
	 * @param includeReachCard 상향(소신) 카드를 1장 이상 넣을지 여부
	 * @return 지망 순서대로 정렬된 선택 결과. 0~6장
	 */
	public List<ScoredCandidate> select(List<ScoredCandidate> candidates, boolean includeReachCard) {
		Selection selection = new Selection(candidates);

		for (CandidateKind stage : STAGE_ORDER) {
			if (selection.isFull()) {
				break;
			}
			for (AdmissionBand band : TARGET_ORDER) {
				selection.addWhile(stage, band, () -> selection.count(band) < TARGET_PER_BAND);
			}
			for (AdmissionBand band : FILL_ORDER) {
				selection.addWhile(stage, band, () -> true);
			}
		}

		for (CandidateKind stage : STAGE_ORDER) {
			for (AdmissionBand band : LAST_RESORT_ORDER) {
				selection.addWhile(stage, band, () -> true);
			}
		}

		if (includeReachCard && selection.count(AdmissionBand.REACH) == 0) {
			selection.forceOneReach();
		}

		return selection.ranked();
	}

	/** 한 번의 선택 과정에서 고른 카드와 제한 조건 상태를 들고 있는 작업 객체 */
	private static final class Selection {

		private final Map<CandidateKind, Map<AdmissionBand, List<ScoredCandidate>>> pool =
				new EnumMap<>(CandidateKind.class);
		private final List<ScoredCandidate> selected = new ArrayList<>();
		private final Map<Long, Integer> universityCounts = new HashMap<>();
		private final Set<String> departmentKeys = new HashSet<>();

		Selection(List<ScoredCandidate> candidates) {
			for (ScoredCandidate candidate : candidates) {
				pool.computeIfAbsent(candidate.kind(), kind -> new EnumMap<>(AdmissionBand.class))
						.computeIfAbsent(candidate.band(), band -> new ArrayList<>())
						.add(candidate);
			}
			for (Map<AdmissionBand, List<ScoredCandidate>> bands : pool.values()) {
				for (List<ScoredCandidate> list : bands.values()) {
					list.sort(PREFERENCE);
				}
			}
		}

		boolean isFull() {
			return selected.size() >= RecommendationResult.MAX_CARDS;
		}

		int count(AdmissionBand band) {
			int count = 0;
			for (ScoredCandidate card : selected) {
				if (card.band() == band) {
					count++;
				}
			}
			return count;
		}

		/** 조건이 참인 동안, 6장이 찰 때까지 해당 경로·구간의 후보를 선호 순서대로 담는다. */
		void addWhile(CandidateKind stage, AdmissionBand band, BooleanSupplier condition) {
			for (ScoredCandidate candidate : candidates(stage, band)) {
				if (isFull() || !condition.getAsBoolean()) {
					return;
				}
				tryAdd(candidate);
			}
		}

		/**
		 * 상향 카드를 요청했는데 하나도 못 담았을 때, 덜 아까운 카드를 빼고 소신 카드 1장을 넣는다.
		 * 대학당 한도나 같은 학과 제한 때문에 넣지 못할 수 있으므로, 소신 후보마다 뺄 수 있는 카드를
		 * 덜 아까운 순서로 모두 시도한다. 같은 대학의 카드를 빼야 들어가는 경우도 여기서 처리된다.
		 */
		void forceOneReach() {
			for (CandidateKind stage : STAGE_ORDER) {
				for (ScoredCandidate candidate : candidates(stage, AdmissionBand.REACH)) {
					if (!isFull()) {
						if (tryAdd(candidate)) {
							return;
						}
					}
					for (ScoredCandidate victim : evictionOrder()) {
						remove(victim);
						if (tryAdd(candidate)) {
							return;
						}
						tryAdd(victim);
					}
				}
			}
		}

		/** 지망 순서대로 정렬한 결과. 같은 구간 안에서는 고른 순서를 지킨다. */
		List<ScoredCandidate> ranked() {
			List<ScoredCandidate> result = new ArrayList<>(selected);
			result.sort(Comparator.comparingInt(card -> RANK_ORDER.indexOf(card.band())));
			return result;
		}

		private List<ScoredCandidate> candidates(CandidateKind stage, AdmissionBand band) {
			Map<AdmissionBand, List<ScoredCandidate>> bands = pool.get(stage);
			if (bands == null) {
				return List.of();
			}
			return bands.getOrDefault(band, List.of());
		}

		private boolean tryAdd(ScoredCandidate candidate) {
			if (universityCounts.getOrDefault(candidate.universityId(), 0) >= MAX_PER_UNIVERSITY) {
				return false;
			}
			if (departmentKeys.contains(candidate.departmentKey())) {
				return false;
			}
			selected.add(candidate);
			universityCounts.merge(candidate.universityId(), 1, Integer::sum);
			departmentKeys.add(candidate.departmentKey());
			return true;
		}

		private void remove(ScoredCandidate candidate) {
			selected.remove(candidate);
			universityCounts.merge(candidate.universityId(), -1, Integer::sum);
			departmentKeys.remove(candidate.departmentKey());
		}

		/**
		 * 뺄 수 있는 카드를 덜 아까운 순서로 돌려준다. 소신 카드는 빼지 않는다.
		 * 보충 카드(평가 불가 → 먼 카드 → 과잉) 다음에, 많이 담은 구간(안정·적정)의 마지막에 담은 카드 순서다.
		 */
		private List<ScoredCandidate> evictionOrder() {
			List<ScoredCandidate> order = new ArrayList<>();
			for (int i = LAST_RESORT_ORDER.size() - 1; i >= 0; i--) {
				addReversed(order, LAST_RESORT_ORDER.get(i));
			}
			List<AdmissionBand> crowdedFirst = new ArrayList<>(List.of(AdmissionBand.SAFE, AdmissionBand.MATCH));
			crowdedFirst.sort(Comparator.comparingInt(this::count).reversed());
			for (AdmissionBand band : crowdedFirst) {
				addReversed(order, band);
			}
			return order;
		}

		private void addReversed(List<ScoredCandidate> order, AdmissionBand band) {
			for (int i = selected.size() - 1; i >= 0; i--) {
				if (selected.get(i).band() == band) {
					order.add(selected.get(i));
				}
			}
		}

	}

	/** 구간 안의 선호 순서. 목표 가능성에 가까운 순, 같으면 대학 이름·모집단위 ID 순으로 항상 같은 결과가 나오게 한다. */
	private static final Comparator<ScoredCandidate> PREFERENCE = Comparator
			.comparingDouble(CardSelector::distanceFromPreferred)
			.thenComparing(ScoredCandidate::universityName)
			.thenComparingLong(ScoredCandidate::unitId);

	private static double distanceFromPreferred(ScoredCandidate candidate) {
		Double preferred = PREFERRED_PROBABILITY.get(candidate.band());
		if (preferred == null || candidate.probability() == null) {
			return 0;
		}
		return Math.abs(candidate.probability() - preferred);
	}

}
