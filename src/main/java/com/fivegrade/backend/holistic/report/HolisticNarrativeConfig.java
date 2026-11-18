package com.fivegrade.backend.holistic.report;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 활동 요약·학과별 조언 작성기를 고른다. 기본은 템플릿이다.
 * 리포트와 같은 스위치(HOLISTIC_REPORT_AI_ENABLED, OPENAI_API_KEY)를 쓰며, 실패하면 템플릿으로 대신한다.
 */
@Configuration(proxyBeanMethods = false)
public class HolisticNarrativeConfig {

	@Bean
	public HolisticNarrativeWriter holisticNarrativeWriter(
			@Value("${HOLISTIC_REPORT_AI_ENABLED:false}") boolean enabled,
			@Value("${OPENAI_API_KEY:}") String apiKey,
			@Value("${HOLISTIC_REPORT_AI_MODEL:gpt-4.1-mini-2025-04-14}") String model,
			@Value("${HOLISTIC_REPORT_AI_TIMEOUT_MS:20000}") long timeoutMillis,
			@Value("${HOLISTIC_REPORT_AI_MAX_CONCURRENT:4}") int maxConcurrent
	) {
		HolisticNarrativeTemplate template = new HolisticNarrativeTemplate();
		if (!enabled) {
			return template;
		}
		return new AiHolisticNarrativeGenerator(
				new OpenAiHolisticNarrativeClient(apiKey, model, Duration.ofMillis(timeoutMillis), maxConcurrent),
				template);
	}

	@Bean
	public HolisticNarrativeSettings holisticNarrativeSettings(
			@Value("${HOLISTIC_NARRATIVE_EVIDENCE_ENABLED:false}") boolean evidenceEnabled
	) {
		return new HolisticNarrativeSettings(evidenceEnabled);
	}
}
