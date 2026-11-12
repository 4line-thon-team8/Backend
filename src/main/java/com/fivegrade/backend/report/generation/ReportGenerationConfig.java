package com.fivegrade.backend.report.generation;

import com.fivegrade.backend.simulation.service.SimulationReportAssembler;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 비활성화 시 키 없이 템플릿만 구성한다. */
@Configuration(proxyBeanMethods = false)
public class ReportGenerationConfig {

    @Bean
    public ReportGenerator reportGenerator(
            @Value("${REPORT_AI_ENABLED:false}") boolean enabled,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${REPORT_AI_MODEL:gpt-4.1-mini-2025-04-14}") String model
    ) {
        if (!enabled) {
            return new TemplateReportGenerator();
        }
        return new AiReportGenerator(new OpenAiReportClient(apiKey, model, Duration.ofSeconds(12)));
    }

    @Bean
    public SimulationReportAssembler simulationReportAssembler(ReportGenerator generator) {
        return new SimulationReportAssembler(generator);
    }
}
