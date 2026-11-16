package com.fivegrade.backend.holistic.scoring;

import java.util.List;

/**
 * 한 후보 전형에 적용할 서류 입력. 후보마다 전형 프로필이 달라 서류 수준과 비중도 후보마다 다르다.
 *
 * @param level 그 전형 기준의 서류 수준(1~5)
 * @param share 전형 전체에서 서류가 차지하는 비중(0~1). 모르면 null
 * @param basis 서류 수준에 크게 작용한 평가요소 이름. 설명용이며 계산에는 쓰지 않는다.
 */
public record DocumentSignal(double level, Double share, List<String> basis) {

	public DocumentSignal {
		basis = basis == null ? List.of() : List.copyOf(basis);
	}

	public DocumentSignal(double level, Double share) {
		this(level, share, List.of());
	}

}
