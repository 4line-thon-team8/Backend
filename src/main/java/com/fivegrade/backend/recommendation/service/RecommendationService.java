package com.fivegrade.backend.recommendation.service;

import com.fivegrade.backend.recommendation.dto.RecommendationQuery;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;

/**
 * 사용자의 5등급 평균과 지원 방향으로 추천 원서를 고른다.
 *
 * SimulationService 는 이 인터페이스에만 의존한다. 후보 선정 구현(B-10)이 완성되기 전에는
 * 테스트에서 이 인터페이스의 가짜 구현을 넣어 환산 → 추천 → 리포트 연결을 먼저 확인할 수 있다.
 *
 * 계산 순서는 다음과 같다.
 * 1. 5등급 평균을 9등급 기준 값으로 환산한다.
 * 2. 희망 학과(필요하면 비슷한 학과·인접 전형·자율전공)의 후보 모집단위를 고른다.
 * 3. 후보마다 합격 가능성 구간을 계산해 구간과 지망 순서를 정한다.
 *
 * 5등급 → 9등급 변환은 이 서비스 안에서 한 번만 한다. 호출하는 쪽은 반올림 전 5등급 평균을 그대로 넘긴다.
 *
 * 후보 부족과 오류는 구분한다.
 * - 후보가 6장에 못 미치거나 하나도 없는 것은 정상 결과다. 카드는 있는 만큼만 담고 warnings 로 알린다.
 * - 계산 실패나 DB 오류는 정상 결과가 아니므로 예외를 던진다. 빈 추천 결과로 바꿔서 돌려주지 않는다.
 *
 * 구현은 사용자의 성적과 희망 학과 값을 로그나 예외 메시지에 남기지 않는다.
 */
public interface RecommendationService {

	RecommendationResult recommend(RecommendationQuery query);

}
