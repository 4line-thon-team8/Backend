package com.fivegrade.backend.holistic.scoring;

/**
 * 추천 서비스가 서류를 반영할 때 받는 입력. 사용자의 요소별 수준과 전형 구성 제한에 쓰는 전체 서류 수준이다.
 *
 * @param levels       3축(학업·진로·공동체) 요소 수준. 후보 전형마다 비중을 달리해 서류 수준을 만든다.
 * @param overallLevel 전형 비중을 가정하지 않은 전체 서류 수준(1~5). 학종 장수 상한을 정하는 데 쓴다.
 */
public record DocumentContext(DocumentLevelCalculator.ElementLevels levels, double overallLevel) {
}
