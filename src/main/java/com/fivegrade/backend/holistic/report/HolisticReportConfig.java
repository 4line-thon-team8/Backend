package com.fivegrade.backend.holistic.report;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 리포트 글 작성기를 고른다. 기본은 템플릿이다.
 * HOLISTIC_REPORT_AI_ENABLED=true 이고 OPENAI_API_KEY 가 있을 때만 AI를 쓰며, 실패하면 템플릿으로 대신한다.
 */
@Configuration(proxyBeanMethods = false)
public class HolisticReportConfig {

	@Bean
	public HolisticReportWriter holisticReportWriter(
			@Value("${HOLISTIC_REPORT_AI_ENABLED:false}") boolean enabled,
			@Value("${OPENAI_API_KEY:}") String apiKey,
			@Value("${HOLISTIC_REPORT_AI_MODEL:gpt-4.1-mini-2025-04-14}") String model,
			@Value("${HOLISTIC_REPORT_AI_TIMEOUT_MS:20000}") long timeoutMillis,
			@Value("${HOLISTIC_REPORT_AI_MAX_CONCURRENT:4}") int maxConcurrent
	) {
		HolisticReportTemplate template = new HolisticReportTemplate();
		if (!enabled) {
			return template;
		}
		return new AiHolisticReportGenerator(
				new OpenAiHolisticReportClient(apiKey, model, Duration.ofMillis(timeoutMillis), maxConcurrent),
				template);
	}
}
