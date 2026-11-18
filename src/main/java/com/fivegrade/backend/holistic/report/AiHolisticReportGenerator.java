package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import com.fivegrade.backend.report.generation.AiReportFailure;
import com.fivegrade.backend.report.generation.ReportSection;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI가 문장을 쓰고, 서버가 검증한 뒤에만 쓴다. 호출 실패·검증 실패는 모두 기본 문장(템플릿)으로 대신한다.
 * 안내 문장(caveat)은 항상 서버 고정 문장이다. 로그에는 고정 코드만 남기고 내용은 남기지 않는다.
 */
public final class AiHolisticReportGenerator implements HolisticReportWriter {

	private static final Logger LOG = LoggerFactory.getLogger(AiHolisticReportGenerator.class);

	private final HolisticReportAiClient client;
	private final HolisticReportTemplate template;

	public AiHolisticReportGenerator(HolisticReportAiClient client, HolisticReportTemplate template) {
		this.client = Objects.requireNonNull(client);
		this.template = Objects.requireNonNull(template);
	}

	@Override
	public HolisticReportContent generate(HolisticReportInput input) {
		try {
			ParsedReport parsed = client.generate(HolisticReportPrompt.inputOf(input));
			String rejection = HolisticReportValidator.rejection(input, parsed);
			if (rejection != null) {
				LOG.warn("event=holistic_report_ai_fallback reason={} detail={}",
						AiReportFailure.Reason.VALIDATION_REJECTED, rejection);
				return template.generate(input);
			}
			return new HolisticReportContent(
					ReportSection.llm(parsed.summary().text().strip()),
					sections(parsed.strengths()),
					sections(parsed.toDevelop()),
					sections(parsed.nextSteps()),
					ReportSection.template(HolisticReportTemplate.caveatText(input)));
		} catch (Exception failure) {
			LOG.warn("event=holistic_report_ai_fallback reason={}", AiReportFailure.reasonOf(failure));
			return template.generate(input);
		}
	}

	private static List<ReportSection> sections(List<Line> lines) {
		return lines.stream().map(line -> ReportSection.llm(line.text().strip())).toList();
	}
}
