package com.fivegrade.backend.report.generation;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 설명 생성기만 등록한다. 조립기는 SimulationConfig에서 등록한다. */
@Configuration(proxyBeanMethods = false)
public class ReportGenerationConfig {

    @Bean
    public ReportGenerator reportGenerator(
            @Value("${REPORT_AI_ENABLED:false}") boolean enabled,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${REPORT_AI_MODEL:gpt-4.1-mini-2025-04-14}") String model,
            @Value("${REPORT_AI_TIMEOUT_MS:12000}") long timeoutMillis,
            @Value("${REPORT_AI_MAX_CONCURRENT:4}") int maxConcurrent
    ) {
        if (!enabled) {
            return new TemplateReportGenerator();
        }

        return new AiReportGenerator(
                new OpenAiReportClient(
                        apiKey,
                        model,
                        Duration.ofMillis(timeoutMillis),
                        maxConcurrent
                )
        );
    }
}
