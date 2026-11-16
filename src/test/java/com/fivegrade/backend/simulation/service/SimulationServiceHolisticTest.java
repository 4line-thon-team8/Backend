package com.fivegrade.backend.simulation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.conversion.calculation.FiveGradeAverageCalculator;
import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import com.fivegrade.backend.holistic.scoring.DocumentContext;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.ElementLevels;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import com.fivegrade.backend.holistic.service.HolisticEvaluation.SkipReason;
import com.fivegrade.backend.recommendation.dto.RecommendationQuery;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.service.RecommendationService;
import com.fivegrade.backend.simulation.dto.ApplicationPreference;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationRequest;
import com.fivegrade.backend.simulation.dto.SubjectInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 서술 분석 평가 결과에 따라 추천 호출이 갈리는지 확인한다. DB와 Spring 없이 실행한다.
 */
class SimulationServiceHolisticTest {

	private final FiveGradeAverageCalculator calculator = new FiveGradeAverageCalculator();
	private final RecommendationQueryMapper mapper = new DefaultRecommendationQueryMapper();

	/** 어느 쪽 추천 메서드가 불렸는지 기록하는 가짜 추천 서비스 */
	private static final class RecordingRecommendation implements RecommendationService {
		int plainCalls;
		int documentCalls;
		DocumentContext received;

		@Override
		public RecommendationResult recommend(RecommendationQuery query) {
			plainCalls++;
			return RecommendationResult.empty();
		}

		@Override
		public RecommendationResult recommend(RecommendationQuery query, DocumentContext context) {
			documentCalls++;
			received = context;
			return RecommendationResult.empty();
		}
	}

	@Test
	void 서류를_반영하면_서류_입력을_넘겨_추천하고_평가_결과를_담는다() {
		RecordingRecommendation recommendation = new RecordingRecommendation();
		DocumentContext context = new DocumentContext(new ElementLevels(4.0, 3.0, 2.0), 3.0);
		HolisticEvaluation applied = new HolisticEvaluation.Applied(
				"0.1.0", Map.of("A3", 4.0), Map.of("ACADEMIC", 4.0), context);
		var service = new SimulationService(calculator, mapper, recommendation, request -> applied);

		SimulationAnalysis analysis = service.analyzeValidated(request());

		assertEquals(1, recommendation.documentCalls);
		assertEquals(0, recommendation.plainCalls);
		assertSame(context, recommendation.received);
		assertSame(applied, analysis.holistic());
	}

	@Test
	void 반영하지_않으면_기존_추천을_부르고_이유를_담는다() {
		RecordingRecommendation recommendation = new RecordingRecommendation();
		HolisticEvaluation skipped = new HolisticEvaluation.Skipped(SkipReason.RULES_OUTDATED);
		var service = new SimulationService(calculator, mapper, recommendation, request -> skipped);

		SimulationAnalysis analysis = service.analyzeValidated(request());

		assertEquals(0, recommendation.documentCalls);
		assertEquals(1, recommendation.plainCalls);
		assertNull(recommendation.received);
		assertSame(skipped, analysis.holistic());
	}

	@Test
	void 평가기에는_요청의_서술_분석을_그대로_넘긴다() {
		HolisticAnalysisRequest[] seen = new HolisticAnalysisRequest[1];
		var service = new SimulationService(calculator, mapper, new RecordingRecommendation(), request -> {
			seen[0] = request;
			return new HolisticEvaluation.Skipped(SkipReason.NOT_PROVIDED);
		});

		service.analyzeValidated(request());

		assertNull(seen[0]);
	}

	@Test
	void 평가기를_주지_않는_구성은_서술_분석을_보내지_않은_것으로_본다() {
		RecordingRecommendation recommendation = new RecordingRecommendation();
		var service = new SimulationService(calculator, mapper, recommendation);

		SimulationAnalysis analysis = service.analyzeValidated(request());

		assertEquals(1, recommendation.plainCalls);
		assertTrue(analysis.holistic() instanceof HolisticEvaluation.Skipped skipped
				&& skipped.reason() == SkipReason.NOT_PROVIDED);
	}

	private SimulationRequest request() {
		return new SimulationRequest(
				List.of(new SubjectInput("row-1", "SUBJ_0001", 2)),
				new ApplicationPreference(List.of("MAJOR_TEST"), null, null, null),
				true,
				null);
	}

}
