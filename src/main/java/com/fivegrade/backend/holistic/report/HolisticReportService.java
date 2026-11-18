package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import com.fivegrade.backend.holistic.report.HolisticReportInput.ElementView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.IndicatorView;
import com.fivegrade.backend.holistic.report.HolisticReportInput.NotObserved;
import com.fivegrade.backend.holistic.report.HolisticReportResponse.ElementItem;
import com.fivegrade.backend.holistic.report.HolisticReportResponse.IndicatorItem;
import com.fivegrade.backend.holistic.report.HolisticReportResponse.NotMeasuredItem;
import com.fivegrade.backend.holistic.report.HolisticReportResponse.NotObservedItem;
import com.fivegrade.backend.holistic.report.HolisticReportResponse.ReportItem;
import com.fivegrade.backend.holistic.report.HolisticReportResponse.TextItem;
import com.fivegrade.backend.holistic.rules.HolisticRuleCatalog;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import com.fivegrade.backend.holistic.service.HolisticEvaluator;
import com.fivegrade.backend.report.generation.ReportSection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 서술 분석 요청으로 생기부 분석 리포트를 만든다. 저장하지 않는다.
 *
 * 수준은 요청에 실려 온 값을 믿지 않고 {@link HolisticEvaluator}로 서버가 다시 계산한다.
 * 서술 분석을 반영하지 못하면 시뮬레이션과 같은 사유로 SKIPPED 를 돌려준다. 오류가 아니다.
 * 요청의 근거 구절(evidence)은 쓰지 않는다. 사용자가 보낸 신호와 구절은 로그에 남기지 않는다.
 */
@Service
public class HolisticReportService {

	private final HolisticEvaluator evaluator;
	private final HolisticRuleCatalog catalog;
	private final HolisticReportInputFactory inputFactory = new HolisticReportInputFactory();
	private final HolisticReportWriter writer;

	public HolisticReportService(HolisticEvaluator evaluator, HolisticRuleCatalog catalog,
			HolisticReportWriter writer) {
		this.evaluator = evaluator;
		this.catalog = catalog;
		this.writer = writer;
	}

	public HolisticReportResponse create(HolisticAnalysisRequest request) {
		HolisticEvaluation evaluation = evaluator.evaluate(request);
		if (!(evaluation instanceof HolisticEvaluation.Applied applied)) {
			HolisticEvaluation.Skipped skipped = (HolisticEvaluation.Skipped) evaluation;
			return HolisticReportResponse.skipped(skipped.reason());
		}

		HolisticReportInput input = createInput(applied, request);
		HolisticReportContent content = writer.generate(input);
		return toResponse(applied.rulesVersion(), input, content);
	}

	/** 평가 결과와 요청으로 리포트 입력 자료를 만든다. 활동 요약·학과별 조언도 같은 자료를 쓴다. */
	public HolisticReportInput createInput(HolisticEvaluation.Applied applied, HolisticAnalysisRequest request) {
		return inputFactory.create(
				indicatorDefs(),
				observableIds(),
				applied.indicatorLevels(),
				applied.elementLevels(),
				applied.overallLevel(),
				observedYears(request),
				tagCounts(request),
				request.coverage().sectionChars().total());
	}

	private List<HolisticReportInputFactory.IndicatorDef> indicatorDefs() {
		return catalog.indicators().stream()
				.map(indicator -> new HolisticReportInputFactory.IndicatorDef(
						indicator.id(), indicator.label(), indicator.element()))
				.toList();
	}

	/** 규칙이 하나라도 있는 지표. 이 지표만 관찰할 수 있다. */
	private Set<String> observableIds() {
		Set<String> ids = new HashSet<>();
		catalog.rules().forEach(rule -> ids.add(rule.indicator()));
		return ids;
	}

	private static Map<String, List<Integer>> observedYears(HolisticAnalysisRequest request) {
		Map<String, List<Integer>> years = new HashMap<>();
		request.indicatorSignals().forEach(signal -> years.put(signal.id(), signal.years()));
		return years;
	}

	private static List<HolisticReportInputFactory.TagCount> tagCounts(HolisticAnalysisRequest request) {
		return request.interestTags().stream()
				.map(tag -> new HolisticReportInputFactory.TagCount(tag.tag(), tag.count()))
				.toList();
	}

	private static HolisticReportResponse toResponse(
			String rulesVersion, HolisticReportInput input, HolisticReportContent content) {
		return new HolisticReportResponse(
				"APPLIED",
				null,
				rulesVersion,
				input.elements().stream().map(HolisticReportService::elementItem).toList(),
				input.notMeasured().stream().map(item -> new NotMeasuredItem(item.id(), item.label())).toList(),
				new ReportItem(
						text(content.summary()),
						content.strengths().stream().map(HolisticReportService::text).toList(),
						content.toDevelop().stream().map(HolisticReportService::text).toList(),
						content.nextSteps().stream().map(HolisticReportService::text).toList(),
						text(content.caveat())),
				HolisticReportResponse.DISCLAIMER);
	}

	private static ElementItem elementItem(ElementView element) {
		return new ElementItem(
				element.id(),
				element.label(),
				element.band() == null ? null : element.band().name(),
				element.level(),
				element.indicators().stream().map(HolisticReportService::indicatorItem).toList(),
				element.notObserved().stream().map(HolisticReportService::notObservedItem).toList());
	}

	private static IndicatorItem indicatorItem(IndicatorView indicator) {
		return new IndicatorItem(indicator.id(), indicator.label(), indicator.band().name(), indicator.level(),
				indicator.observedYears(), indicator.reliability().name());
	}

	private static NotObservedItem notObservedItem(NotObserved indicator) {
		return new NotObservedItem(indicator.id(), indicator.label());
	}

	private static TextItem text(ReportSection section) {
		return new TextItem(section.text(), section.generationMethod());
	}
}
