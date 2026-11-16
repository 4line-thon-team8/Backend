package com.fivegrade.backend.holistic.scoring;

import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.recommendation.probability.AdmissionBandCalculator;
import com.fivegrade.backend.recommendation.probability.BandAssessment;
import com.fivegrade.backend.recommendation.probability.CutForecast;
import com.fivegrade.backend.recommendation.probability.ProbabilityParameters;
import java.util.List;
import java.util.OptionalDouble;

/**
 * 서류 수준으로 한 후보의 합격 가능성 구간을 보정한다.
 *
 * <pre>
 * σ_unit = max(w, widthFloor) / widthToSigma          w: 예측 컷 폭
 * δ      = λ(m) · σ_unit · shareFactor(서류 비중)      m: 서류 수준(1~5)
 * g'     = g − δ                                       등급은 작을수록 우수하므로 δ&gt;0이 유리
 * σ'     = √(σ² + (sigmaDocRatio · σ_unit)²)
 * </pre>
 * 구간 분류는 기존 {@link AdmissionBandCalculator}를 그대로 쓰고, 결과 구간이 원래 구간에서
 * {@code maxBandShift}단계(초기 1) 넘게 움직이지 않게 막는다.
 * 안정·적정·소신만 보정하고, 과잉·먼 카드·평가 불가는 그대로 둔다. 서류로 먼 카드를 소신으로 올리면
 * 근거가 약한 낙관을 주기 때문이다.
 *
 * Spring과 DB에 의존하지 않는 순수 Java 클래스다.
 */
public final class DocumentAdjuster {

	/** 보정이 오갈 수 있는 구간 사다리. 위로 갈수록 합격 가능성이 높다. */
	private static final List<AdmissionBand> LADDER = List.of(
			AdmissionBand.REACH, AdmissionBand.MATCH, AdmissionBand.SAFE);

	private final DocumentAdjustmentParameters parameters;
	private final ProbabilityParameters probabilityParameters;
	private final AdmissionBandCalculator bandCalculator;

	public DocumentAdjuster() {
		this(DocumentAdjustmentParameters.DEFAULT, ProbabilityParameters.DEFAULT);
	}

	public DocumentAdjuster(DocumentAdjustmentParameters parameters, ProbabilityParameters probabilityParameters) {
		this.parameters = parameters;
		this.probabilityParameters = probabilityParameters;
		this.bandCalculator = new AdmissionBandCalculator(probabilityParameters);
	}

	/**
	 * @param forecast      과거 입결 예측. null이면 평가 불가라 보정하지 않는다.
	 * @param studentNine   컷과 같은 기준(9등급)의 학생 등급
	 * @param base          보정 전 평가 결과
	 * @param documentLevel 후보 전형 기준 서류 수준(1~5). null이면 보정하지 않는다.
	 * @param documentShare 전형 전체에서 서류가 차지하는 비중(0~1). 모르면 null
	 */
	public DocumentAdjustment adjust(
			CutForecast forecast,
			double studentNine,
			BandAssessment base,
			Double documentLevel,
			Double documentShare
	) {
		Double baseProbability = base.probability().isPresent() ? base.probability().getAsDouble() : null;
		if (documentLevel == null) {
			return DocumentAdjustment.none(base.band(), baseProbability, DocumentAdjustment.Reason.NO_DOCUMENT_LEVEL);
		}
		if (forecast == null || !LADDER.contains(base.band())) {
			return DocumentAdjustment.none(base.band(), baseProbability, DocumentAdjustment.Reason.BAND_NOT_ADJUSTABLE);
		}

		double sigmaUnit = Math.max(forecast.width(), parameters.widthFloor()) / parameters.widthToSigma();
		double shift = parameters.lambda(documentLevel) * sigmaUnit * parameters.shareFactor(documentShare);
		// 보정이 없으면(중립 수준 등) 서류 불확실성도 더하지 않는다. 서류가 보통인 사용자의 가능성이 움직이면 안 된다.
		double sigmaDoc = shift == 0.0 ? 0.0 : parameters.sigmaDocRatio() * sigmaUnit;

		BandAssessment adjusted = bandCalculator.assess(forecast, studentNine, shift, sigmaDoc);
		AdmissionBand limited = limit(base.band(), adjusted.band());

		OptionalDouble probability = adjusted.probability();
		return new DocumentAdjustment(
				true,
				shift,
				base.band(),
				limited,
				probability.isPresent() ? probability.getAsDouble() : null,
				DocumentAdjustment.Reason.APPLIED
		);
	}

	/** 결과 구간을 원래 구간에서 maxBandShift단계 안으로 자른다. 사다리 밖(과잉·먼 카드)으로 나가면 끝 단계로 맞춘다. */
	private AdmissionBand limit(AdmissionBand base, AdmissionBand adjusted) {
		int baseIndex = LADDER.indexOf(base);
		int adjustedIndex = indexOnLadder(adjusted);
		int lowest = Math.max(0, baseIndex - parameters.maxBandShift());
		int highest = Math.min(LADDER.size() - 1, baseIndex + parameters.maxBandShift());
		return LADDER.get(Math.max(lowest, Math.min(highest, adjustedIndex)));
	}

	/** 과잉은 안정 위쪽이라 사다리 맨 위로, 먼 카드는 소신 아래라 맨 아래로 본다. */
	private int indexOnLadder(AdmissionBand band) {
		if (band == AdmissionBand.OVER) {
			return LADDER.size() - 1;
		}
		int index = LADDER.indexOf(band);
		return index >= 0 ? index : 0;
	}

	ProbabilityParameters probabilityParameters() {
		return probabilityParameters;
	}

}
