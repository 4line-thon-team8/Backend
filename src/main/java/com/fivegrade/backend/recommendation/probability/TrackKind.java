package com.fivegrade.backend.recommendation.probability;

/**
 * 입결 행의 품질 판정과 불확실성(σ) 계산에서 구분하는 전형 계열.
 *
 * DB의 track_type 문자열을 이 값으로 바꿔서 쓴다.
 * 교과·학종이 아닌 값이나 비어 있는 값은 OTHER이며, 교과와 같은 기준을 적용한다.
 */
public enum TrackKind {

	SUBJECT("교과", 1.5),
	HOLISTIC("학종", 1.0),
	OTHER("", 1.5);

	private final String label;
	private final double maxCleanWidth;

	TrackKind(String label, double maxCleanWidth) {
		this.label = label;
		this.maxCleanWidth = maxCleanWidth;
	}

	/**
	 * 70%컷과 50%컷의 차이가 이 값을 넘으면 신뢰하기 어려운 행으로 본다.
	 */
	public double maxCleanWidth() {
		return maxCleanWidth;
	}

	public static TrackKind fromLabel(String label) {
		for (TrackKind kind : values()) {
			if (kind != OTHER && kind.label.equals(label)) {
				return kind;
			}
		}
		return OTHER;
	}
}
