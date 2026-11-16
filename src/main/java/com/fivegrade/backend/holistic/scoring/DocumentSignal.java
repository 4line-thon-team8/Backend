package com.fivegrade.backend.holistic.scoring;

/**
 * 한 후보 전형에 적용할 서류 입력. 후보마다 전형 프로필이 달라 서류 수준과 비중도 후보마다 다르다.
 *
 * @param level 그 전형 기준의 서류 수준(1~5)
 * @param share 전형 전체에서 서류가 차지하는 비중(0~1). 모르면 null
 */
public record DocumentSignal(double level, Double share) {
}
