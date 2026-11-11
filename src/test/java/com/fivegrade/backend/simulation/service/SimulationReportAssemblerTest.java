package com.fivegrade.backend.simulation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.report.generation.ReportGenerator;
import com.fivegrade.backend.report.generation.ReportSection;
import com.fivegrade.backend.report.generation.TemplateReportGenerator;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SimulationReportAssemblerTest {

    private final TemplateReportGenerator generator = new TemplateReportGenerator();
    private final SimulationReportAssembler assembler =
            new SimulationReportAssembler(generator);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 6})
    void preservesAvailableCardsAndAllSections(int count) {
        List<RecommendedCard> cards = IntStream.rangeClosed(1, count)
                .mapToObj(rank -> card(rank, AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR))
                .toList();
        var result = assembler.assemble(analysis(new RecommendationResult(cards, List.of())));

        assertEquals(count, result.recommendations().size());
        assertEquals(new BigDecimal("1.13"), result.displayAverage());
        assertEquals("row-1", result.subjects().getFirst().subjectRef());
        assertEquals("row-2", result.subjects().getLast().subjectRef());
        assertEquals(List.of(new BigDecimal("1.00"), new BigDecimal("2.00")),
                result.subjects().stream().map(SimulationAnalysis.SubjectGrade::grade).toList());
        for (int i = 0; i < count; i++) {
            assertSame(cards.get(i), result.recommendations().get(i).card());
            assertEquals("template", result.recommendations().get(i).reason().generationMethod());
        }
        var report = result.report();
        for (var section : List.of(report.greetingSummary(), report.overallAssessment(),
                report.applicationStrategy(), report.closing())) {
            assertFalse(section.text().isBlank());
            assertEquals("template", section.generationMethod());
        }
        assertTrue(report.overallAssessment().text().contains("1.13"));
        assertTrue(report.overallAssessment().text().contains("대학별"));
        assertTrue(report.closing().text().contains("보장하지 않습니다"));
        assertThrows(UnsupportedOperationException.class, () -> result.recommendations().clear());
    }

    @ParameterizedTest
    @EnumSource(AdmissionBand.class)
    void explainsEveryBandWithoutReclassifying(AdmissionBand band) {
        var original = card(1, band, CandidateKind.DESIRED_MAJOR);
        var result = assembler.assemble(analysis(new RecommendationResult(List.of(original), List.of())));
        var explained = result.recommendations().getFirst();

        assertSame(original, explained.card());
        assertEquals(original.gradeGap(), explained.card().gradeGap());
        String expected = switch (band) {
            case SAFE -> "안정";
            case MATCH -> "적정";
            case REACH -> "소신";
            case FAR -> "먼 구간";
            case OVER -> "여유가 큰";
            case UNRATED -> "평가하지 못했습니다";
        };
        assertTrue(explained.reason().text().contains(expected));
        assertFalse(explained.reason().text().contains("%"));
        assertFalse(explained.reason().text().contains("0.00"));
        assertEquals(null, explained.card().evidence().competitionRate());
    }

    @ParameterizedTest
    @EnumSource(CandidateKind.class)
    void explainsCandidateSource(CandidateKind kind) {
        var result = assembler.assemble(analysis(new RecommendationResult(
                List.of(card(1, AdmissionBand.MATCH, kind)), List.of()
        )));
        String expected = switch (kind) {
            case DESIRED_MAJOR -> "희망 학과에 해당";
            case SIMILAR_MAJOR -> "비슷한 학과";
            case OTHER_TRACK -> "다른 전형";
            case UNDECLARED -> "학과를 정하지 않고";
        };
        assertTrue(result.recommendations().getFirst().reason().text().contains(expected));
    }

    @ParameterizedTest
    @EnumSource(WarningCode.class)
    void preservesAndExplainsEachWarningAtItsScope(WarningCode code) {
        boolean global = switch (code) {
            case NO_CANDIDATES, FEWER_THAN_SIX_CARDS, REACH_CARD_NOT_AVAILABLE -> true;
            default -> false;
        };
        var warning = global ? RecommendationWarning.forResult(code)
                : RecommendationWarning.forCard(code, 1);
        List<RecommendedCard> cards = code == WarningCode.NO_CANDIDATES ? List.of()
                : List.of(card(1, code == WarningCode.UNRATED_CARD
                ? AdmissionBand.UNRATED : AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR));
        var baseline = assembler.assemble(analysis(new RecommendationResult(cards, List.of())));
        var result = assembler.assemble(analysis(new RecommendationResult(cards, List.of(warning))));

        assertEquals(List.of(warning), result.warnings());
        String baselineText = global ? baseline.report().applicationStrategy().text()
                : baseline.recommendations().getFirst().reason().text();
        String actualText = global ? result.report().applicationStrategy().text()
                : result.recommendations().getFirst().reason().text();
        assertTrue(actualText.length() > baselineText.length());
    }

    @Test
    void doesNotAttachCardWarningToAnotherCard() {
        var warning = RecommendationWarning.forCard(WarningCode.LOW_CONFIDENCE_CARD, 2);
        var result = assembler.assemble(analysis(new RecommendationResult(
                List.of(card(1, AdmissionBand.SAFE, CandidateKind.DESIRED_MAJOR),
                        card(2, AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR)), List.of(warning)
        )));
        assertFalse(result.recommendations().getFirst().reason().text().contains("제한적인"));
        assertTrue(result.recommendations().getLast().reason().text().contains("제한적인"));
    }

    @Test
    void rejectsMissingAndMisorderedReasons() {
        var input = analysis(new RecommendationResult(
                List.of(card(1, AdmissionBand.MATCH, CandidateKind.DESIRED_MAJOR)), List.of()
        ));
        var report = generator.generate(input.displayAverage(), input.recommendation()).report();
        var missing = new SimulationReportAssembler((average, recommendation) ->
                new ReportGenerator.GeneratedReport(report, List.of()));
        var misordered = new SimulationReportAssembler((average, recommendation) ->
                new ReportGenerator.GeneratedReport(report, List.of(
                        new ReportGenerator.CardReason(2, ReportSection.template("가상 설명")))));
        assertThrows(IllegalStateException.class, () -> missing.assemble(input));
        assertThrows(IllegalStateException.class, () -> misordered.assemble(input));
    }

    @Test
    void generationFailurePropagates() {
        var failure = new IllegalStateException("generation unavailable");
        var failing = new SimulationReportAssembler((average, recommendation) -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> failing.assemble(analysis(RecommendationResult.empty()))));
    }

    private SimulationAnalysis analysis(RecommendationResult recommendation) {
        return new SimulationAnalysis(new BigDecimal("1.13"), List.of(
                new SimulationAnalysis.SubjectGrade("row-1", "SUBJ_0001", new BigDecimal("1.00")),
                new SimulationAnalysis.SubjectGrade("row-2", "SUBJ_0001", new BigDecimal("2.00"))
        ), recommendation);
    }

    private RecommendedCard card(int rank, AdmissionBand band, CandidateKind kind) {
        return new RecommendedCard(rank, rank, "가상대학", "가상학과" + rank, "가상전형",
                band, kind, band == AdmissionBand.UNRATED ? null : -0.123456789,
                new CardEvidence(null, null, null, 0));
    }
}
