package com.fivegrade.backend.simulation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.report.generation.ConsultingReport;
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

    private final TemplateReportGenerator generator =
            new TemplateReportGenerator();

    private final SimulationReportAssembler assembler =
            new SimulationReportAssembler(generator);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 6})
    void preservesAvailableCardsAndAllSections(int count) {
        List<RecommendedCard> cards = IntStream.rangeClosed(1, count)
                .mapToObj(rank -> card(
                        rank,
                        AdmissionBand.MATCH,
                        CandidateKind.DESIRED_MAJOR
                ))
                .toList();

        var result = assembler.assemble(
                analysis(new RecommendationResult(cards, List.of()))
        );

        assertEquals(count, result.recommendations().size());
        assertEquals(
                new BigDecimal("1.13"),
                result.displayAverage()
        );
        assertEquals(
                "row-1",
                result.subjects().getFirst().subjectRef()
        );
        assertEquals(
                "row-2",
                result.subjects().getLast().subjectRef()
        );
        assertEquals(
                List.of(
                        new BigDecimal("1.00"),
                        new BigDecimal("2.00")
                ),
                result.subjects().stream()
                        .map(SimulationAnalysis.SubjectGrade::grade)
                        .toList()
        );

        for (int i = 0; i < count; i++) {
            assertSame(
                    cards.get(i),
                    result.recommendations().get(i).card()
            );
            assertEquals(
                    "template",
                    result.recommendations().get(i)
                            .reason().generationMethod()
            );
        }

        var report = result.report();

        for (var section : List.of(
                report.greetingSummary(),
                report.overallAssessment(),
                report.applicationStrategy(),
                report.closing()
        )) {
            assertFalse(section.text().isBlank());
            assertEquals("template", section.generationMethod());
        }

        assertTrue(
                report.overallAssessment().text().contains("1.13")
        );
        assertTrue(
                report.overallAssessment().text().contains("대학별")
        );
        assertTrue(
                report.overallAssessment().text()
                        .contains("입력한 과목의 5등급 기준")
        );
        assertFalse(
                report.overallAssessment().text()
                        .contains("국수영사과")
        );
        assertFalse(
                report.applicationStrategy().text()
                        .contains("임의로")
        );

        String allSections = reportText(report);

        if (count == 0) {
            assertEquals(
                    1,
                    occurrences(
                            allSections,
                            expectedWarning(
                                    WarningCode.NO_CANDIDATES,
                                    count
                            )
                    )
            );
        } else if (count < RecommendationResult.MAX_CARDS) {
            assertEquals(
                    0,
                    occurrences(
                            allSections,
                            expectedWarning(
                                    WarningCode.FEWER_THAN_SIX_CARDS,
                                    count
                            )
                    )
            );
            assertEquals(
                    "현재 조건에서 추천 가능한 후보는 "
                            + count
                            + "장이에요.",
                    TemplateReportGenerator.warningText(
                            WarningCode.FEWER_THAN_SIX_CARDS,
                            count
                    )
            );
        }

        assertTrue(
                report.closing().text()
                        .contains("최종 지원 결정은 직접 판단해 주세요")
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.recommendations().clear()
        );
    }

    @ParameterizedTest
    @EnumSource(AdmissionBand.class)
    void explainsEveryBandWithoutReclassifying(AdmissionBand band) {
        var original = card(
                1,
                band,
                CandidateKind.DESIRED_MAJOR
        );

        var result = assembler.assemble(
                analysis(new RecommendationResult(
                        List.of(original),
                        List.of()
                ))
        );

        var explained = result.recommendations().getFirst();

        assertSame(original, explained.card());
        assertEquals(
                original.gradeGap(),
                explained.card().gradeGap()
        );

        String expected = switch (band) {
            case SAFE -> "여유 있게";
            case MATCH -> "비슷한 수준";
            case REACH -> "도전해 볼 수 있어요";
            case FAR -> "신중하게";
            case OVER -> "충분히 앞서는";
            case UNRATED -> "자료가 부족해요";
        };

        assertTrue(explained.reason().text().contains(expected));
        assertFalse(explained.reason().text().contains("%"));
        assertFalse(explained.reason().text().contains("0.00"));
        assertEquals(
                null,
                explained.card().evidence().competitionRate()
        );
    }

    @ParameterizedTest
    @EnumSource(CandidateKind.class)
    void preservesCandidateKindWithoutRepeatingExpansionInCard(
            CandidateKind kind
    ) {
        var original = card(
                1,
                AdmissionBand.MATCH,
                kind
        );

        var result = assembler.assemble(
                analysis(new RecommendationResult(
                        List.of(original),
                        List.of()
                ))
        );

        assertSame(
                original,
                result.recommendations().getFirst().card()
        );

        String text = result.recommendations().getFirst()
                .reason().text();

        for (var code : List.of(
                WarningCode.SIMILAR_MAJOR_USED,
                WarningCode.UNDECLARED_USED
        )) {
            assertFalse(text.contains(expectedWarning(code, 1)));
        }

        assertFalse(text.contains("선호 전형"));
        assertFalse(text.contains("넓힌 후보"));
    }

    @ParameterizedTest
    @EnumSource(WarningCode.class)
    void preservesInternalWarningsAndProvidesApprovedPublicMessage(
            WarningCode code
    ) {
        boolean global = switch (code) {
            case NO_CANDIDATES,
                 FEWER_THAN_SIX_CARDS,
                 REACH_CARD_NOT_AVAILABLE -> true;
            default -> false;
        };

        var warning = global
                ? RecommendationWarning.forResult(code)
                : RecommendationWarning.forCard(code, 1);

        CandidateKind kind = switch (code) {
            case SIMILAR_MAJOR_USED -> CandidateKind.SIMILAR_MAJOR;
            case OTHER_TRACK_USED -> CandidateKind.OTHER_TRACK;
            case UNDECLARED_USED -> CandidateKind.UNDECLARED;
            default -> CandidateKind.DESIRED_MAJOR;
        };

        List<RecommendedCard> cards =
                code == WarningCode.NO_CANDIDATES
                        ? List.of()
                        : List.of(card(
                        1,
                        code == WarningCode.UNRATED_CARD
                                ? AdmissionBand.UNRATED
                                : AdmissionBand.MATCH,
                        kind
                ));

        var warnings = List.of(warning, warning);

        var result = assembler.assemble(
                analysis(new RecommendationResult(cards, warnings))
        );

        assertEquals(warnings, result.warnings());

        String expected = expectedWarning(code, cards.size());

        assertEquals(
                expected,
                TemplateReportGenerator.warningText(
                        code,
                        cards.size()
                )
        );

        if (code == WarningCode.OTHER_TRACK_USED) {
            assertEquals("", expected);
        } else if (code == WarningCode.NO_CANDIDATES) {
            assertEquals(
                    1,
                    occurrences(reportText(result.report()), expected)
            );
        } else {
            assertFalse(
                    reportText(result.report()).contains(expected)
            );

            if (code == WarningCode.UNRATED_CARD) {
                assertEquals(
                        1,
                        occurrences(
                                result.recommendations().getFirst()
                                        .reason().text(),
                                expected
                        )
                );
            } else {
                assertFalse(
                        result.recommendations().getFirst()
                                .reason().text().contains(expected)
                );
            }
        }
    }

    @Test
    void keepsLowConfidenceRankForPublicMapperWithoutRepeatingInReason() {
        var warning = RecommendationWarning.forCard(
                WarningCode.LOW_CONFIDENCE_CARD,
                2
        );

        var result = assembler.assemble(
                analysis(new RecommendationResult(
                        List.of(
                                card(
                                        1,
                                        AdmissionBand.SAFE,
                                        CandidateKind.DESIRED_MAJOR
                                ),
                                card(
                                        2,
                                        AdmissionBand.MATCH,
                                        CandidateKind.DESIRED_MAJOR
                                )
                        ),
                        List.of(warning)
                ))
        );

        assertEquals(List.of(warning), result.warnings());
        assertEquals(2, result.warnings().getFirst().rank());

        String expected = expectedWarning(
                WarningCode.LOW_CONFIDENCE_CARD,
                2
        );

        result.recommendations().forEach(item ->
                assertFalse(item.reason().text().contains(expected))
        );
    }

    @Test
    void rejectsMissingAndMisorderedReasons() {
        var input = analysis(new RecommendationResult(
                List.of(card(
                        1,
                        AdmissionBand.MATCH,
                        CandidateKind.DESIRED_MAJOR
                )),
                List.of()
        ));

        var report = generator.generate(
                input.displayAverage(),
                input.recommendation()
        ).report();

        var missing = new SimulationReportAssembler(
                (average, recommendation) ->
                        new ReportGenerator.GeneratedReport(
                                report,
                                List.of()
                        )
        );

        var misordered = new SimulationReportAssembler(
                (average, recommendation) ->
                        new ReportGenerator.GeneratedReport(
                                report,
                                List.of(
                                        new ReportGenerator.CardReason(
                                                2,
                                                ReportSection.template(
                                                        "가상 설명"
                                                )
                                        )
                                )
                        )
        );

        assertThrows(
                IllegalStateException.class,
                () -> missing.assemble(input)
        );
        assertThrows(
                IllegalStateException.class,
                () -> misordered.assemble(input)
        );
    }

    @Test
    void generationFailurePropagates() {
        var failure = new IllegalStateException(
                "generation unavailable"
        );

        var failing = new SimulationReportAssembler(
                (average, recommendation) -> {
                    throw failure;
                }
        );

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> failing.assemble(
                                analysis(RecommendationResult.empty())
                        )
                )
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rejectsUnknownWarningRankBeforeGeneration(int cardCount) {
        List<RecommendedCard> cards =
                IntStream.rangeClosed(1, cardCount)
                        .mapToObj(rank -> card(
                                rank,
                                AdmissionBand.MATCH,
                                CandidateKind.DESIRED_MAJOR
                        ))
                        .toList();

        // 정상 DTO 생성자는 잘못된 rank를 이미 거부하므로 대역으로 검증한다.
        var malformed = mock(RecommendationResult.class);

        when(malformed.cards()).thenReturn(cards);
        when(malformed.warnings()).thenReturn(
                List.of(
                        RecommendationWarning.forCard(
                                WarningCode.LOW_CONFIDENCE_CARD,
                                cardCount + 1
                        )
                )
        );

        var reportGenerator = mock(ReportGenerator.class);

        var checkedAssembler =
                new SimulationReportAssembler(reportGenerator);

        var failure = assertThrows(
                IllegalStateException.class,
                () -> checkedAssembler.assemble(analysis(malformed))
        );

        assertEquals(
                "warning rank does not refer to an existing card",
                failure.getMessage()
        );

        verifyNoInteractions(reportGenerator);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void normalRecommendationDtoAlreadyRejectsUnknownWarningRank(
            int cardCount
    ) {
        List<RecommendedCard> cards =
                IntStream.rangeClosed(1, cardCount)
                        .mapToObj(rank -> card(
                                rank,
                                AdmissionBand.MATCH,
                                CandidateKind.DESIRED_MAJOR
                        ))
                        .toList();

        assertThrows(
                IllegalArgumentException.class,
                () -> new RecommendationResult(
                        cards,
                        List.of(
                                RecommendationWarning.forCard(
                                        WarningCode.LOW_CONFIDENCE_CARD,
                                        cardCount + 1
                                )
                        )
                )
        );
    }

    @Test
    void usesApprovedGreetingAverageAndClosingWithoutFixedYearDisclaimer() {
        var result = assembler.assemble(
                analysis(new RecommendationResult(
                        List.of(card(
                                1,
                                AdmissionBand.MATCH,
                                CandidateKind.DESIRED_MAJOR
                        )),
                        List.of()
                ))
        );

        var report = result.report();

        assertEquals(
                "입력해 주신 성적과 지원 방향을 바탕으로 수시 원서 추천 리스트를 안내해 드려요.",
                report.greetingSummary().text()
        );

        assertTrue(
                report.overallAssessment().text().contains(
                        "입력한 과목의 5등급 기준 성적을 같은 비중으로 계산한 참고 평균이에요. "
                                + "대학별 입결의 반영 교과·가중치·산출 방식과 달라 비교에 오차가 있을 수 있어요."
                )
        );

        assertEquals(
                "이 리포트는 과거 입결을 바탕으로 한 참고용 추정이에요. "
                        + "최신 모집요강을 꼭 확인하고, 최종 지원 결정은 직접 판단해 주세요.",
                report.closing().text()
        );

        assertFalse(reportText(report).contains("2027"));
        assertFalse(reportText(report).contains("습니다"));
    }

    private String reportText(ConsultingReport report) {
        return String.join(
                " ",
                report.greetingSummary().text(),
                report.overallAssessment().text(),
                report.applicationStrategy().text(),
                report.closing().text()
        );
    }

    private int occurrences(String text, String expected) {
        int count = 0;
        int offset = 0;

        while ((offset = text.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }

        return count;
    }

    private String expectedWarning(
            WarningCode code,
            int count
    ) {
        return switch (code) {
            case NO_CANDIDATES ->
                    "조건에 맞는 원서를 찾지 못했어요. 희망 학과나 선호 전형을 바꿔서 다시 시도해 주세요.";

            case FEWER_THAN_SIX_CARDS ->
                    "현재 조건에서 추천 가능한 후보는 "
                            + count
                            + "장이에요.";

            case REACH_CARD_NOT_AVAILABLE ->
                    "요청하신 소신 카드는 조건에 맞는 후보가 없어 포함하지 못했어요.";

            case SIMILAR_MAJOR_USED ->
                    "일부 후보는 희망 학과와 비슷한 학과로 넓혀 찾았어요.";

            case OTHER_TRACK_USED ->
                    "";

            case UNDECLARED_USED ->
                    "자율전공·무전공 후보는 학과를 정하지 않고 뽑는 모집단위예요. "
                            + "입학 후 전공 선택 방식을 확인해 주세요.";

            case UNRATED_CARD ->
                    "지원 수준을 판단할 입결 자료가 부족해요. 최신 모집요강과 대학의 입시 결과를 확인해 주세요.";

            case LOW_CONFIDENCE_CARD ->
                    "추천 근거가 되는 입결 자료가 적어 신뢰도가 다소 낮으니 결과 해석에 주의해 주세요";
        };
    }

    private SimulationAnalysis analysis(
            RecommendationResult recommendation
    ) {
        return new SimulationAnalysis(
                new BigDecimal("1.13"),
                List.of(
                        new SimulationAnalysis.SubjectGrade(
                                "row-1",
                                "SUBJ_0001",
                                new BigDecimal("1.00")
                        ),
                        new SimulationAnalysis.SubjectGrade(
                                "row-2",
                                "SUBJ_0001",
                                new BigDecimal("2.00")
                        )
                ),
                recommendation
        );
    }

    private RecommendedCard card(
            int rank,
            AdmissionBand band,
            CandidateKind kind
    ) {
        return new RecommendedCard(
                rank,
                rank,
                "가상대학",
                "가상학과" + rank,
                "가상전형",
                band,
                kind,
                band == AdmissionBand.UNRATED
                        ? null
                        : -0.123456789,
                new CardEvidence(null, null, null, 0)
        );
    }
}
