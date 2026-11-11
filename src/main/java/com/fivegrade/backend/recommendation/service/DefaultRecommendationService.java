package com.fivegrade.backend.recommendation.service;

import com.fivegrade.backend.recommendation.candidate.CandidateUnit;
import com.fivegrade.backend.recommendation.candidate.RecommendationCandidateLoader;
import com.fivegrade.backend.recommendation.dto.RecommendationQuery;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.scale.GradeScaleConverter;
import com.fivegrade.backend.recommendation.selection.CandidateScorer;
import com.fivegrade.backend.recommendation.selection.CardSelector;
import com.fivegrade.backend.recommendation.selection.RecommendationAssembler;
import com.fivegrade.backend.recommendation.selection.ScoredCandidate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 서비스 구현. 후보 조회 → 구간 계산 → 카드 선택 → 결과 조립 순서로 잇는다.
 *
 * 각 단계의 규칙은 단계별 클래스에 있고 이 클래스는 순서와 트랜잭션만 맡는다.
 * 5등급 → 9등급 변환은 여기서 한 번만 하며, 반올림 전 5등급 평균으로 gradeGap 도 계산한다.
 * 후보가 없는 것은 정상 결과(빈 카드와 안내)이고, 계산 실패나 DB 오류는 예외로 그대로 던진다.
 * 사용자의 성적과 희망 학과 값은 로그나 예외 메시지에 남기지 않는다.
 */
@Service
public class DefaultRecommendationService implements RecommendationService {

	private final RecommendationCandidateLoader candidateLoader;
	private final GradeScaleConverter scaleConverter = new GradeScaleConverter();
	private final CandidateScorer scorer = new CandidateScorer();
	private final CardSelector selector = new CardSelector();
	private final RecommendationAssembler assembler = new RecommendationAssembler();

	public DefaultRecommendationService(RecommendationCandidateLoader candidateLoader) {
		this.candidateLoader = candidateLoader;
	}

	@Override
	@Transactional(readOnly = true)
	public RecommendationResult recommend(RecommendationQuery query) {
		double fiveAverage = query.fiveGradeAverage().doubleValue();
		double studentNine = scaleConverter.toNineGradeEquivalent(fiveAverage);

		List<Integer> recentYears = candidateLoader.recentCutYears();
		List<CandidateUnit> units = candidateLoader.load(query);

		List<ScoredCandidate> scored = units.stream()
				.map(unit -> scorer.score(unit, recentYears, studentNine, fiveAverage))
				.toList();

		List<ScoredCandidate> selected = selector.select(scored, query.includeReachCard());
		return assembler.assemble(selected, query.includeReachCard());
	}

}
