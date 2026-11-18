package com.fivegrade.backend.holistic.report;

import com.fivegrade.backend.holistic.dto.HolisticAnalysisRequest;
import com.fivegrade.backend.holistic.report.HolisticNarrativeFactsFactory.DesiredMajor;
import com.fivegrade.backend.holistic.report.HolisticNarrativeFactsFactory.EvidenceInput;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 시뮬레이션 응답에 붙일 활동 요약·학과별 조언을 만든다. 저장하지 않는다.
 * 호출하는 쪽은 {@link #narrate}만 부르면 된다. 어떤 경우에도 예외를 내지 않고 결과(또는 빈 결과)를 돌려준다.
 *
 * 근거 구절은 외부 AI 전송 동의가 앱에 들어가기 전에는 쓰지 않는다
 * (HOLISTIC_NARRATIVE_EVIDENCE_ENABLED, 기본 꺼짐).
 */
@Service
public class HolisticNarrativeService {

	private final HolisticReportService reportService;
	private final MajorTagProvider majorTags;
	private final HolisticNarrativeWriter writer;
	private final HolisticStrengthSelector strengthSelector = HolisticStrengthSelector.withDefaults();
	private final HolisticNarrativeSettings settings;

	public HolisticNarrativeService(HolisticReportService reportService, MajorTagProvider majorTags,
			HolisticNarrativeWriter writer, HolisticNarrativeSettings settings) {
		this.reportService = reportService;
		this.majorTags = majorTags;
		this.writer = writer;
		this.settings = settings;
	}

	/**
	 * @param applied 서버가 계산한 서술 분석 결과
	 * @param request 서술 분석 요청(관심 태그·근거 구절을 읽는다)
	 * @param desired 희망 학과(입력 순서)
	 */
	public HolisticNarrative narrate(HolisticEvaluation.Applied applied, HolisticAnalysisRequest request,
			List<DesiredMajor> desired) {
		try {
			HolisticReportInput report = reportService.createInput(applied, request);
			List<EvidenceInput> evidence = settings.evidenceEnabled()
					? request.evidence().stream()
							.map(phrase -> new EvidenceInput(phrase.text(), phrase.year(), phrase.section()))
							.toList()
					: List.of();
			HolisticNarrativeFacts facts = HolisticNarrativeFactsFactory.create(
					report,
					strengthSelector.select(applied.indicatorLevels()),
					desired,
					majorTags.tagsOf(desired.stream().map(DesiredMajor::code).toList()),
					evidence);
			return writer.write(facts);
		} catch (RuntimeException failure) {
			// 요약·조언은 덤이다. 만들지 못해도 시뮬레이션 응답은 그대로 내려간다.
			return HolisticNarrative.empty();
		}
	}
}
