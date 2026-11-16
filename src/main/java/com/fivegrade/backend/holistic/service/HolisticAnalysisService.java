package com.fivegrade.backend.holistic.service;

import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import com.fivegrade.backend.holistic.rules.HolisticRuleCatalog;
import com.fivegrade.backend.holistic.scoring.DocumentContext;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator;
import com.fivegrade.backend.holistic.scoring.ElementLevelCalculator;
import com.fivegrade.backend.holistic.service.HolisticEvaluation.SkipReason;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 서술 분석 요청을 서류 수준으로 바꾼다.
 *
 * 순서: 보내지 않음 → 기능 꺼짐 → 규칙 버전 → 사전 검증 → 서술 분량 → 신호 유무 → 지표·요소 수준 → 서류 수준.
 * 규칙 버전이 맞지 않으면 사전과 대조하는 검증을 하지 않는다. 지표 ID가 달라졌을 수 있기 때문이다.
 * 사전 검증에 실패하면 400 예외가 그대로 올라간다. 그 외 반영하지 못한 경우는 이유를 담은 Skipped 로 답한다.
 *
 * 기능은 HOLISTIC_ENABLED 로 켠다(기본 꺼짐). 서술 분석을 요청에 담아도 꺼져 있으면 반영하지 않는다.
 * 사용자가 보낸 구절과 신호 값은 로그나 예외 메시지에 남기지 않는다.
 */
@Service
public class HolisticAnalysisService implements HolisticEvaluator {

	private final HolisticRuleCatalog catalog;
	private final HolisticRequestValidator validator;
	private final boolean enabled;
	private final ElementLevelCalculator elementCalculator = new ElementLevelCalculator();
	private final DocumentLevelCalculator documentCalculator = new DocumentLevelCalculator();

	public HolisticAnalysisService(
			HolisticRuleCatalog catalog,
			HolisticRequestValidator validator,
			@Value("${HOLISTIC_ENABLED:false}") boolean enabled
	) {
		this.catalog = catalog;
		this.validator = validator;
		this.enabled = enabled;
	}

	@Override
	public HolisticEvaluation evaluate(HolisticAnalysisRequest request) {
		if (request == null) {
			return skipped(SkipReason.NOT_PROVIDED);
		}
		if (!enabled) {
			return skipped(SkipReason.DISABLED);
		}
		if (!validator.isAcceptedVersion(request.rulesVersion())) {
			return skipped(SkipReason.RULES_OUTDATED);
		}
		validator.validate(request);
		if (request.coverage().sectionChars().total() < catalog.limits().minNarrativeChars()) {
			return skipped(SkipReason.TOO_SHORT);
		}
		// 신호가 하나도 안 잡힌 것은 근거가 없다는 뜻이 아니라 측정하지 못했다는 뜻일 수 있다.
		// 신호가 없는 지표는 평균에서 빠지므로(A안) 판단할 재료가 없으면 서류를 반영하지 않는다.
		if (request.indicatorSignals().isEmpty()) {
			return skipped(SkipReason.INSUFFICIENT_SIGNALS);
		}

		List<ElementLevelCalculator.Observation> observations = request.indicatorSignals().stream()
				.map(signal -> new ElementLevelCalculator.Observation(signal.id(), signal.count(), signal.quality()))
				.toList();
		ElementLevelCalculator.Result levels = elementCalculator.compute(observableIndicators(), observations);

		Optional<Double> overall = documentCalculator.compute(
				levels.axisLevels(), DocumentLevelCalculator.AxisWeights.equal());
		if (overall.isEmpty()) {
			return skipped(SkipReason.INSUFFICIENT_SIGNALS);
		}
		return new HolisticEvaluation.Applied(
				request.rulesVersion(),
				levels.indicatorLevels(),
				levels.elementLevels(),
				new DocumentContext(levels.axisLevels(), overall.get()));
	}

	/** 규칙이 하나라도 있는 지표만 관찰할 수 있다. 규칙이 없는 지표는 측정하지 못한 것이다. */
	private Map<String, String> observableIndicators() {
		Map<String, String> elementOf = new HashMap<>();
		catalog.indicators().forEach(indicator -> elementOf.put(indicator.id(), indicator.element()));
		Map<String, String> observable = new HashMap<>();
		catalog.rules().forEach(rule -> observable.put(rule.indicator(), elementOf.get(rule.indicator())));
		return observable;
	}

	private static HolisticEvaluation skipped(SkipReason reason) {
		return new HolisticEvaluation.Skipped(reason);
	}

}
