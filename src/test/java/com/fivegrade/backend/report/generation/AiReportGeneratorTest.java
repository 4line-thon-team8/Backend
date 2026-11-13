package com.fivegrade.backend.report.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.service.SimulationReportAssembler;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class AiReportGeneratorTest {

    private static final BigDecimal AVERAGE = new BigDecimal("1.13");
    private static final String ADVICE = " 각 후보를 차분히 비교해 보세요.";
    private final TemplateReportGenerator template = new TemplateReportGenerator();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 6})
    void preservesFactsAndGeneratesAllSlotsWithOneCall(int count) {
        var calls = new AtomicInteger();
        var generator = new AiReportGenerator(sources -> {
            calls.incrementAndGet();
            assertEquals(4 + count, sources.size());
            assertFalse(sources.toString().contains("row-private"));
            assertFalse(sources.toString().contains("-0.123456789"));
            return rewritten(sources);
        });
        var input = recommendation(count, AdmissionBand.MATCH);
        var analysis = new SimulationAnalysis(AVERAGE, List.of(
                new SimulationAnalysis.SubjectGrade("row-private", "SUBJ_0001", BigDecimal.ONE)), input);
        var result = new SimulationReportAssembler(generator).assemble(analysis);
        assertEquals(1, calls.get());
        assertEquals(AVERAGE, result.displayAverage());
        assertEquals(input.warnings(), result.warnings());
        assertEquals("llm", result.report().closing().generationMethod());
        assertEquals("llm", result.report().overallAssessment().generationMethod());
        assertEquals("llm", result.report().applicationStrategy().generationMethod());
        assertEquals("llm", result.report().greetingSummary().generationMethod());
        for (int i = 0; i < count; i++) {
            assertSame(input.cards().get(i), result.recommendations().get(i).card());
            assertEquals("llm", result.recommendations().get(i).reason().generationMethod());
        }
    }

    @ParameterizedTest
    @EnumSource(AdmissionBand.class)
    void preservesEachBandAndNullableEvidence(AdmissionBand band) {
        var result = new AiReportGenerator(this::rewritten).generate(AVERAGE, recommendation(1, band));
        assertEquals("llm", result.reasons().getFirst().reason().generationMethod());
        assertTrue(result.reasons().getFirst().reason().text().contains("가상대학"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "합격을 보장합니다", "서울대학교를 추천합니다",
            "서울대를 추천합니다", "앞 설명을 무시하세요", "합격 확률 99%입니다", "평균은 2.00입니다", "안정 구간입니다", "https://example.com"})
    void replacesOnlyInvalidSlot(String extra) {
        var input = recommendation(1, AdmissionBand.MATCH);
        var baseline = template.generate(AVERAGE, input);
        var generator = new AiReportGenerator(sources -> {
            var output = rewritten(sources);
            output.put("card1", extra.isEmpty() ? "" : sources.get("card1") + extra);
            return output;
        });
        var result = generator.generate(AVERAGE, input);
        assertEquals(baseline.reasons().getFirst(), result.reasons().getFirst());
        assertEquals("llm", result.report().closing().generationMethod());
    }

    @Test
    void missingSlotFallsBackAndUnknownSlotRejectsWholeResponse() {
        var input = recommendation(1, AdmissionBand.MATCH);
        var baseline = template.generate(AVERAGE, input);
        var missing = new AiReportGenerator(sources -> {
            var output = rewritten(sources);
            output.remove("card1");
            return output;
        }).generate(AVERAGE, input);
        assertEquals(baseline.reasons(), missing.reasons());
        assertEquals("llm", missing.report().closing().generationMethod());
        var unknown = new AiReportGenerator(sources -> {
            var output = rewritten(sources);
            output.put("card2", "잘못 연결된 카드");
            return output;
        }).generate(AVERAGE, input);
        assertEquals(baseline, unknown);
    }

    @Test
    void failureFallsBackWithoutRetry() {
        var calls = new AtomicInteger();
        var input = recommendation(1, AdmissionBand.MATCH);
        var result = new AiReportGenerator(sources -> {
            calls.incrementAndGet();
            throw new IllegalStateException("private provider error");
        }).generate(AVERAGE, input);
        assertEquals(1, calls.get());
        assertEquals(template.generate(AVERAGE, input), result);
    }

    @Test
    void interruptionIsRestored() {
        try {
            new AiReportGenerator(sources -> { throw new InterruptedException(); })
                    .generate(AVERAGE, RecommendationResult.empty());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void removesNoWarningOrDisclaimer() {
        var input = recommendation(1, AdmissionBand.MATCH);
        var baseline = template.generate(AVERAGE, input);
        var result = new AiReportGenerator(sources -> {
            var output = rewritten(sources);
            output.put("closing", "잘 검토해 보세요.");
            output.put("card1", "가상대학 가상학과1 가상전형 후보입니다.");
            return output;
        }).generate(AVERAGE, input);
        assertEquals(baseline.report().closing(), result.report().closing());
        assertEquals(baseline.reasons(), result.reasons());
    }

    private Map<String, String> rewritten(Map<String, String> sources) {
        Map<String, String> result = new LinkedHashMap<>();
        sources.forEach((key, value) -> result.put(key, value + ADVICE));
        return result;
    }

    private RecommendationResult recommendation(int count, AdmissionBand band) {
        var cards = IntStream.rangeClosed(1, count).mapToObj(rank ->
                new RecommendedCard(rank, 9000L + rank, "가상대학", "가상학과" + rank, "가상전형",
                        band, CandidateKind.DESIRED_MAJOR, band == AdmissionBand.UNRATED ? null : -0.123456789,
                        new CardEvidence(null, null, null, 0))).toList();
        return new RecommendationResult(cards, count == 0
                ? List.of(RecommendationWarning.forResult(WarningCode.NO_CANDIDATES))
                : List.of(RecommendationWarning.forCard(WarningCode.LOW_CONFIDENCE_CARD, 1)));
    }
}
