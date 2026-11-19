package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.report.HolisticNarrative.Detail;
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
				LOG.warn("event=holistic_narrative_ai_partial_fallback reason={} codes={} details={}",
						AiReportFailure.Reason.VALIDATION_REJECTED, checked.rejections(), checked.diagnostics());
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
		return new HolisticNarrative(summary, advices, mergeDetail(fallback.detail(), checked));
	}

	/**
	 * 자세히 보기. 전체 요약·강점 근거·보완할 점·다음 단계를 검증을 통과한 AI 글로 바꾼다.
	 * 영역마다 통과한 글이 하나도 없으면 그 영역만 기본 문장을 쓴다. 다음 단계의 마지막은 언제나 서버 고정 문장이다.
	 */
	private static Detail mergeDetail(Detail fallback, Checked checked) {
		if (fallback == null) {
			return null;
		}
		Text overview = checked.overview() != null
				? Text.llm(checked.overview().text().strip())
				: fallback.overview();
		List<Text> evidence = checked.strengthEvidence().isEmpty()
				? fallback.strengthEvidence()
				: checked.strengthEvidence().stream().map(line -> Text.llm(line.text().strip())).toList();
		List<Text> improvements = checked.improvements().isEmpty()
				? fallback.improvements()
				: checked.improvements().stream().map(line -> Text.llm(line.text().strip())).toList();
		return new Detail(overview, evidence, improvements, mergeNextSteps(fallback.nextSteps(), checked));
	}

	/** AI 다음 단계(최대 2개) 뒤에 서버 고정 문장을 붙인다. AI 글이 없으면 기본 문장 전체를 그대로 쓴다. */
	private static List<Text> mergeNextSteps(List<Text> fallback, Checked checked) {
		if (checked.nextSteps().isEmpty()) {
			return fallback;
		}
		List<Text> steps = new ArrayList<>(checked.nextSteps().stream()
				.map(line -> Text.llm(line.text().strip())).toList());
		steps.add(Text.template(HolisticNarrativeTemplate.FIXED_NEXT_STEP));
		return steps;
	}
}
