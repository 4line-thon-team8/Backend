package com.fivegrade.backend.holistic.report;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 관찰한 지표 수준에서 역량 강점(최대 3개)을 고른다. 값을 새로 계산하지 않는 순수 함수다.
 *
 * 고르는 기준: 구간이 HIGH 이고 모델 신뢰도가 LOW 가 아닌 지표를 수준이 높은 순으로, 같으면 지표 ID 순으로 최대 3개.
 * 신뢰도가 낮은 지표는 높게 나와도 강점으로 내리지 않는다(잘못 칭찬하는 것을 막는다).
 * 설명 문장은 지표별로 서버가 정한 고정 문장이며 화면에 한 줄로 나오도록 22자 이내로 쓴다. 이름은 /api/holistic-rules 의 label 과 같다.
 */
public final class HolisticStrengthSelector {

	public static final int MAX_STRENGTHS = 3;

	/** 지표 ID → 이름·강점 설명. 규칙 파일(rules-0.7.0.json)의 label 과 맞춘다. */
	private static final Map<String, Text> TEXTS = Map.ofEntries(
			Map.entry("A2", new Text("학업태도", "학습에 꾸준히 임하는 태도가 드러나요.")),
			Map.entry("A3", new Text("탐구력", "스스로 파고드는 탐구 활동이 보여요.")),
			Map.entry("B3", new Text("진로 탐색 활동과 경험", "진로와 관련한 탐색 경험이 풍부해요.")),
			Map.entry("B4", new Text("전공 연계 일관성", "활동이 희망 전공과 일관되게 이어져요.")),
			Map.entry("C1", new Text("협업과 소통능력", "협력하고 소통한 경험이 보여요.")),
			Map.entry("C2", new Text("나눔과 배려", "주변을 배려하고 나누는 모습이 드러나요.")),
			Map.entry("C3", new Text("성실성과 규칙준수", "맡은 일을 성실히 해내는 모습이 보여요.")),
			Map.entry("C4", new Text("리더십", "팀을 이끌고 책임진 경험이 보여요.")),
			Map.entry("D1", new Text("자기주도성", "스스로 계획하고 실행한 경험이 보여요.")),
			Map.entry("D2", new Text("경험의 다양성", "다양한 분야를 경험한 폭이 보여요.")),
			Map.entry("D3", new Text("창의적 문제해결력", "문제를 새롭게 풀어낸 경험이 보여요.")),
			Map.entry("D4", new Text("심화·확장", "배운 내용을 깊고 넓게 확장해 가요.")));

	private final HolisticReportParameters parameters;

	public HolisticStrengthSelector(HolisticReportParameters parameters) {
		this.parameters = parameters;
	}

	public static HolisticStrengthSelector withDefaults() {
		return new HolisticStrengthSelector(HolisticReportParameters.DEFAULT);
	}

	/**
	 * @param indicatorLevels 관찰한 지표 ID → 수준(1~5)
	 * @return 강한 순 최대 3개. 없으면 빈 목록
	 */
	public List<Strength> select(Map<String, Double> indicatorLevels) {
		return indicatorLevels.entrySet().stream()
				.filter(entry -> TEXTS.containsKey(entry.getKey()))
				.filter(entry -> parameters.bandOf(entry.getValue()) == ReportBand.HIGH)
				.filter(entry -> parameters.reliabilityOf(entry.getKey()) != HolisticReportParameters.Reliability.LOW)
				.sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())
						.thenComparing(Map.Entry.comparingByKey()))
				.limit(MAX_STRENGTHS)
				.map(entry -> {
					Text text = TEXTS.get(entry.getKey());
					return new Strength(entry.getKey(), text.label(), parameters.displayLevel(entry.getValue()),
							text.description());
				})
				.toList();
	}

	/** 선택된 강점 하나 */
	public record Strength(String indicatorId, String label, int level, String description) {
	}

	private record Text(String label, String description) {
	}
}
