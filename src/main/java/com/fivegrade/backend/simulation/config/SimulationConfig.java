package com.fivegrade.backend.simulation.config;

import com.fivegrade.backend.conversion.calculation.FiveGradeAverageCalculator;
import com.fivegrade.backend.recommendation.service.RecommendationService;
import com.fivegrade.backend.report.generation.ReportGenerator;
import com.fivegrade.backend.report.generation.TemplateReportGenerator;
import com.fivegrade.backend.simulation.service.DefaultRecommendationQueryMapper;
import com.fivegrade.backend.simulation.service.RecommendationQueryMapper;
import com.fivegrade.backend.simulation.service.SimulationReportAssembler;
import com.fivegrade.backend.simulation.service.SimulationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 분석 API가 쓰는 평균 계산, 추천 연결, 리포트 조립을 Bean으로 등록한다.
 *
 * 해당 클래스들은 Spring 어노테이션 없이 순수 클래스로 만들어 두었기 때문에 여기서 연결한다.
 * AI 리포트(LLM) 생성기를 붙일 때는 reportGenerator Bean만 바꾸면 된다.
 */
@Configuration(proxyBeanMethods = false)
public class SimulationConfig {

	@Bean
	FiveGradeAverageCalculator fiveGradeAverageCalculator() {
		return new FiveGradeAverageCalculator();
	}

	@Bean
	RecommendationQueryMapper recommendationQueryMapper() {
		return new DefaultRecommendationQueryMapper();
	}

	@Bean
	SimulationService simulationService(
			FiveGradeAverageCalculator averageCalculator,
			RecommendationQueryMapper queryMapper,
			RecommendationService recommendationService
	) {
		return new SimulationService(averageCalculator, queryMapper, recommendationService);
	}

	@Bean
	ReportGenerator reportGenerator() {
		return new TemplateReportGenerator();
	}

	@Bean
	SimulationReportAssembler simulationReportAssembler(ReportGenerator reportGenerator) {
		return new SimulationReportAssembler(reportGenerator);
	}

}
