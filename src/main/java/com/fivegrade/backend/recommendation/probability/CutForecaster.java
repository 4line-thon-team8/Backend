package com.fivegrade.backend.recommendation.probability;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 한 모집단위의 과거 입결로 다음 해 컷을 예측한다.
 *
 * 최근 정상 컷 최대 3개 연도의 가중 평균(0.5 / 0.3 / 0.2)을 쓴다.
 * 가중치의 순서는 모집단위가 아니라 전체 데이터의 최근 연도(recentYears) 기준이며,
 * 어떤 연도의 정상 컷이 없으면 남은 연도의 가중치로 다시 정규화한다.
 * 정상 컷이 하나도 없을 때만 가장 최근의 불량 행을 대신 쓰고, 이때 연수는 0이다.
 *
 * Spring과 DB에 의존하지 않는 순수 Java 계산기다.
 */
public final class CutForecaster {

	/**
	 * @param cuts        한 모집단위의 연도별 입결(연도 순서는 무관)
	 * @param recentYears 전체 데이터에서 정상 컷이 있는 최근 연도를 최신부터 나열한 것(최대 3개만 쓴다)
	 * @param track       이 모집단위의 전형 계열
	 * @return 사용할 컷이 전혀 없으면 비어 있다.
	 */
	public Optional<CutForecast> forecast(
			List<YearlyCut> cuts,
			List<Integer> recentYears,
			TrackKind track
	) {
		double weightSum = 0;
		double highSum = 0;
		double lowSum = 0;
		int count = 0;

		int usable = Math.min(recentYears.size(), ProbabilityParameters.YEAR_WEIGHTS.length);
		for (int rank = 0; rank < usable; rank++) {
			double weight = ProbabilityParameters.YEAR_WEIGHTS[rank];
			for (YearlyCut cut : cuts) {
				if (cut.year() == recentYears.get(rank) && cut.isClean(track)) {
					highSum += cut.high() * weight;
					lowSum += cut.low() * weight;
					weightSum += weight;
					count++;
				}
			}
		}

		if (count > 0) {
			double high = highSum / weightSum;
			double low = lowSum / weightSum;
			return Optional.of(new CutForecast(high, high - low, count));
		}

		return cuts.stream()
				.filter(cut -> !cut.isClean(track))
				.max(Comparator.comparingInt(YearlyCut::year))
				.map(cut -> new CutForecast(cut.high(), cut.width(), 0));
	}
}
