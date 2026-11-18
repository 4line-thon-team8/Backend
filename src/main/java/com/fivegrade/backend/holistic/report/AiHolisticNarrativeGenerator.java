package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticNarrative.MajorAdvice;
import com.fivegrade.backend.holistic.report.HolisticNarrative.Text;
import com.fivegrade.backend.holistic.report.HolisticNarrativeFacts.MajorFacts;
import com.fivegrade.backend.holistic.report.HolisticNarrativeValidator.Checked;
import com.fivegrade.backend.holistic.report.ParsedReport.Line;
import com.fivegrade.backend.report.generation.AiReportFailure;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI가 문장을 쓰고, 서버가 글 하나씩 검증한 뒤에만 쓴다. 통과하지 못한 글만 기본 문장(템플릿)으로 대신한다.
 * 호출 실패는 전부 기본 문장이다. 어떤 경우에도 예외를 밖으로 내지 않고, 로그에는 고정 코드만 남긴다.
 */
public final class AiHolisticNarrativeGenerator implements HolisticNarrativeWriter {

	private static final Logger LOG = LoggerFactory.getLogger(AiHolisticNarrativeGenerator.class);

	private final HolisticNarrativeAiClient client;
	private final HolisticNarrativeTemplate template;

	public AiHolisticNarrativeGenerator(HolisticNarrativeAiClient client, HolisticNarrativeTemplate template) {
		this.client = Objects.requireNonNull(client);
		this.template = Objects.requireNonNull(template);
	}

	@Override
	public HolisticNarrative write(HolisticNarrativeFacts facts) {
		HolisticNarrative fallback = template.write(facts);
		try {
			ParsedNarrative parsed = client.generate(HolisticNarrativePrompt.inputOf(facts));
			Checked checked = HolisticNarrativeValidator.check(facts, parsed);
			if (!checked.rejections().isEmpty()) {
				LOG.warn("event=holistic_narrative_ai_partial_fallback reason={} codes={}",
						AiReportFailure.Reason.VALIDATION_REJECTED, checked.rejections());
			} else {
				LOG.info("event=holistic_narrative_ai_success");
			}
			return merge(facts, fallback, checked);
		} catch (Exception failure) {
			LOG.warn("event=holistic_narrative_ai_fallback reason={}", AiReportFailure.reasonOf(failure));
			return fallback;
		}
	}

	private static HolisticNarrative merge(HolisticNarrativeFacts facts, HolisticNarrative fallback, Checked checked) {
		Text summary = checked.summary() != null
				? Text.llm(checked.summary().text().strip())
				: fallback.activitySummary();
		List<MajorAdvice> advices = new ArrayList<>();
		for (int i = 0; i < facts.majors().size(); i++) {
			MajorFacts major = facts.majors().get(i);
			Line line = checked.advices().get(major.code());
			advices.add(line != null
					? new MajorAdvice(major.code(), major.department(), Text.llm(line.text().strip()))
					: fallback.majorAdvices().get(i));
		}
		return new HolisticNarrative(summary, advices);
	}
}
