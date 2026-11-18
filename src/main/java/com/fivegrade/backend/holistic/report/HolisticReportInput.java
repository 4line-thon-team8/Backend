package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticReportParameters.Reliability;
import java.util.List;

/**
 * 리포트를 쓰는 데 쓰는 사실 자료. 서버가 확정한 값만 담는다.
 * 생기부 원문, 근거 구절, 이름·학교, 성적, 지원 대학·전형은 담지 않는다. 수준 수치도 구간으로만 남긴다.
 *
 * @param narrativeAmount 읽은 서술 분량
 * @param overallBand     전체 서류 수준 구간
 * @param elements        평가요소 4개(학업·진로·공동체·발전가능성). 판단하지 못한 요소는 band 가 null 이다.
 * @param notMeasured     규칙이 없어 측정하지 않는 지표
 * @param interestTags    전공 키워드 태그(규칙 사전의 값). 빈도가 높은 순, 최대 5개
 */
public record HolisticReportInput(
		NarrativeAmount narrativeAmount,
		ReportBand overallBand,
		List<ElementView> elements,
		List<NotMeasured> notMeasured,
		List<String> interestTags
) {

	public enum NarrativeAmount {
		SUFFICIENT, LIMITED
	}

	/** 신호가 관찰된 지표 */
	public record IndicatorView(
			String id,
			String label,
			ReportBand band,
			int level,
			List<Integer> observedYears,
			Reliability reliability
	) {
		public IndicatorView {
			observedYears = List.copyOf(observedYears);
		}
	}

	/** 관찰할 수 있는 지표인데 이번 분석에서 신호가 잡히지 않은 지표. 없다는 뜻이 아니다. */
	public record NotObserved(String id, String label, Reliability reliability) {
	}

	/** 평가요소 하나. 관찰된 지표가 없으면 band·level 이 null 이다. */
	public record ElementView(
			String id,
			String label,
			ReportBand band,
			Integer level,
			List<IndicatorView> indicators,
			List<NotObserved> notObserved
	) {
		public ElementView {
			indicators = List.copyOf(indicators);
			notObserved = List.copyOf(notObserved);
		}
	}

	public record NotMeasured(String id, String label) {
	}

	public HolisticReportInput {
		elements = List.copyOf(elements);
		notMeasured = List.copyOf(notMeasured);
		interestTags = List.copyOf(interestTags);
	}
}
