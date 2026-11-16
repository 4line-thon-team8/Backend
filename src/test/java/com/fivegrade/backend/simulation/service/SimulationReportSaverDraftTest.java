package com.fivegrade.backend.simulation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.DocumentFit;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.report.generation.ConsultingReport;
import com.fivegrade.backend.report.generation.ReportSection;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import com.fivegrade.backend.simulation.dto.SimulationReportResult.ExplainedCard;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 저장 계약에는 서술 분석이 섞이지 않은 성적 기준 구간이 들어가는지 확인한다. */
class SimulationReportSaverDraftTest {

	@Test
	void 서류_보정을_적용한_카드도_저장_구간은_성적만으로_본_구간이다() {
		ReportSection section = ReportSection.template("문구");
		RecommendedCard adjusted = new RecommendedCard(1, 10L, "가대학교", "컴퓨터공학과", "학생부종합",
				AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR, 0.5, new CardEvidence(30, 4.5, false, 3),
				AdmissionBand.REACH, DocumentFit.of(4.5, List.of("ACADEMIC")));
		RecommendedCard plain = new RecommendedCard(2, 20L, "나대학교", "컴퓨터공학과", "학생부교과",
				AdmissionBand.SAFE, CandidateKind.DESIRED_MAJOR, 1.0, new CardEvidence(30, 4.5, false, 3));
		SimulationReportResult result = new SimulationReportResult(
				new BigDecimal("1.13"),
				List.of(new SimulationAnalysis.SubjectGrade("row-1", "SUBJ_0001", new BigDecimal("1.00"))),
				List.of(new ExplainedCard(adjusted, section), new ExplainedCard(plain, section)),
				List.of(),
				new ConsultingReport(section, section, section, section),
				new HolisticEvaluation.Skipped(HolisticEvaluation.SkipReason.DISABLED));

		var items = SimulationReportSaver.toDraft(result).recommendations();

		assertEquals("REACH", items.get(0).label());
		assertEquals("SAFE", items.get(1).label());
	}

}
