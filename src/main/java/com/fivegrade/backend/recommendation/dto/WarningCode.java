package com.fivegrade.backend.recommendation.dto;

/**
 * 추천 결과에 붙는 가정·자료 부족 안내의 종류.
 *
 * 사용자에게 보여 줄 문구는 여기서 정하지 않는다. 코드만 전달하고 문구는 응답 조립 단계와 기획이 정한다.
 * 카드 하나에 해당하는 안내는 rank 를 함께 담고, 결과 전체에 해당하는 안내는 rank 가 null이다.
 * 문구에 필요한 추가 정보는 별도 필드로 싣지 않고 결과의 카드 값(candidateKind, band, evidence)에서 읽는다.
 *
 * 코드별 발생 조건
 * - NO_CANDIDATES: cards 가 비어 있을 때 항상 하나 붙는다.
 * - FEWER_THAN_SIX_CARDS: cards 가 1~5장일 때 붙는다. 장수는 cards.size() 로 안다.
 * - REACH_CARD_NOT_AVAILABLE: includeReachCard 가 true 인데 REACH 구간 카드를 넣지 못했을 때 붙는다.
 * - SIMILAR_MAJOR_USED / OTHER_TRACK_USED / UNDECLARED_USED: 해당 카드의 candidateKind 가
 *   각각 SIMILAR_MAJOR / OTHER_TRACK / UNDECLARED 일 때 그 카드마다 붙는다.
 * - UNRATED_CARD: 해당 카드의 band 가 UNRATED 일 때(gradeGap 은 null) 붙는다.
 * - LOW_CONFIDENCE_CARD: 구간은 계산했지만 예측 오차 폭(σ)이 커서 신뢰도가 낮을 때 붙는다.
 *   σ 는 정상 컷 연수와 두 컷의 폭으로 정해지며 0.60 을 넘으면 LOW 다.
 *   정상 컷 연수가 0·1년이면 폭과 상관없이 LOW 이고, 2년 이상은 두 컷의 폭이 넓을 때만 LOW 가 된다.
 *   구간을 평가하지 못한 UNRATED 카드에는 붙지 않는다.
 */
public enum WarningCode {

	/** 추천할 후보가 하나도 없다. (전체) */
	NO_CANDIDATES,

	/** 후보가 6장에 못 미쳐 그만큼만 추천했다. (전체) */
	FEWER_THAN_SIX_CARDS,

	/** 상향 카드를 요청했지만 넣을 후보가 없었다. (전체) */
	REACH_CARD_NOT_AVAILABLE,

	/** 희망 학과가 아닌 비슷한 학과로 채운 카드다. (카드) */
	SIMILAR_MAJOR_USED,

	/** 선호 전형이 아닌 전형으로 채운 카드다. (카드) */
	OTHER_TRACK_USED,

	/** 학과를 정하지 않고 뽑는 모집단위로 채운 카드다. (카드) */
	UNDECLARED_USED,

	/** 사용할 입결이 없어 합격 가능성 구간을 평가하지 못한 카드다. (카드) */
	UNRATED_CARD,

	/** 근거가 되는 입결 연수가 적어 구간의 신뢰도가 낮은 카드다. (카드) */
	LOW_CONFIDENCE_CARD

}
