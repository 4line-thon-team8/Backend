package com.fivegrade.backend.report.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

class AiReportObservabilityTest {

    @ParameterizedTest
    @EnumSource(AiReportFailure.Reason.class)
    void recordsOnlyFixedReasonAndKeepsTemplate(
            AiReportFailure.Reason reason
    ) {
        try (var logs = new CapturedLogs()) {
            var result = new AiReportGenerator(sources -> {
                throw new AiReportFailure(reason);
            }).generate(
                    new BigDecimal("2.34"),
                    RecommendationResult.empty()
            );

            var baseline = new TemplateReportGenerator().generate(
                    new BigDecimal("2.34"),
                    RecommendationResult.empty()
            );

            assertEquals(baseline, result);
            assertEquals(1, logs.appender.list.size());

            var event = logs.appender.list.getFirst();

            assertEquals(
                    "event=report_ai_fallback reason="
                            + reason.name()
                            + allFallbackCounts(),
                    event.getFormattedMessage()
            );
            assertNull(event.getThrowableProxy());
            assertFalse(
                    event.getFormattedMessage().contains("2.34")
            );
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void neverLogsUntrustedExceptionOrCause() {
        try (var logs = new CapturedLogs()) {
            new AiReportGenerator(sources -> {
                throw new IllegalStateException(
                        "SECRET_PROMPT average=2.34 key=fake-secret",
                        new RuntimeException("SECRET_RESPONSE")
                );
            }).generate(
                    new BigDecimal("2.34"),
                    RecommendationResult.empty()
            );

            assertEquals(1, logs.appender.list.size());

            assertEquals(
                    "event=report_ai_fallback reason=UNAVAILABLE"
                            + allFallbackCounts(),
                    logs.appender.list.getFirst()
                            .getFormattedMessage()
            );
            assertNull(
                    logs.appender.list.getFirst()
                            .getThrowableProxy()
            );
        }
    }

    @Test
    void reportsInvalidStructureWithoutLoggingOutput() {
        try (var logs = new CapturedLogs()) {
            new AiReportGenerator(sources -> null).generate(
                    BigDecimal.ONE,
                    RecommendationResult.empty()
            );

            assertEquals(
                    "event=report_ai_fallback reason=INVALID_RESPONSE"
                            + allFallbackCounts(),
                    logs.appender.list.getFirst()
                            .getFormattedMessage()
            );
        }
    }

    @Test
    void reportsSectionFallbackOnceWithoutSlotContents() {
        try (var logs = new CapturedLogs()) {
            new AiReportGenerator(sources -> Map.of()).generate(
                    BigDecimal.ONE,
                    RecommendationResult.empty()
            );

            assertEquals(1, logs.appender.list.size());

            assertEquals(
                    "event=report_ai_fallback reason=VALIDATION_REJECTED"
                            + allFallbackCounts(),
                    logs.appender.list.getFirst()
                            .getFormattedMessage()
            );
        }
    }

    @Test
    void countsOnlyRejectedReportSection() {
        try (var logs = new CapturedLogs()) {
            var result = new AiReportGenerator(sources -> {
                var output = new LinkedHashMap<>(sources);
                output.put("overallAssessment", null);
                return output;
            }).generate(
                    BigDecimal.ONE,
                    RecommendationResult.empty()
            );

            assertEquals(
                    "template",
                    result.report()
                            .overallAssessment()
                            .generationMethod()
            );
            assertEquals(
                    "llm",
                    result.report().closing().generationMethod()
            );
            assertEquals(1, logs.appender.list.size());

            assertEquals(
                    "event=report_ai_fallback reason=VALIDATION_REJECTED totalSections=4 "
                            + "fallbackSections=1 greetingSummaryFallback=0 overallAssessmentFallback=1 "
                            + "applicationStrategyFallback=0 closingFallback=0 cardReasonFallbacks=0",
                    logs.appender.list.getFirst()
                            .getFormattedMessage()
            );
        }
    }

    @Test
    void countsRejectedCardsSeparatelyFromReportSections() {
        var card = new RecommendedCard(
                1,
                1L,
                "가상대학",
                "가상학과",
                "가상전형",
                AdmissionBand.MATCH,
                CandidateKind.DESIRED_MAJOR,
                0.1,
                new CardEvidence(null, null, null, 0)
        );

        try (var logs = new CapturedLogs()) {
            var result = new AiReportGenerator(sources -> {
                var output = new LinkedHashMap<>(sources);
                output.put("card1", null);
                return output;
            }).generate(
                    BigDecimal.ONE,
                    new RecommendationResult(
                            List.of(card),
                            List.of()
                    )
            );

            assertEquals(
                    "template",
                    result.reasons().getFirst()
                            .reason().generationMethod()
            );
            assertEquals(
                    "llm",
                    result.report().closing().generationMethod()
            );
            assertEquals(1, logs.appender.list.size());

            assertEquals(
                    "event=report_ai_fallback reason=VALIDATION_REJECTED totalSections=5 "
                            + "fallbackSections=1 greetingSummaryFallback=0 overallAssessmentFallback=0 "
                            + "applicationStrategyFallback=0 closingFallback=0 cardReasonFallbacks=1",
                    logs.appender.list.getFirst()
                            .getFormattedMessage()
            );
        }
    }

    private static String allFallbackCounts() {
        return " totalSections=4 fallbackSections=4 "
                + "greetingSummaryFallback=1 overallAssessmentFallback=1 "
                + "applicationStrategyFallback=1 closingFallback=1 "
                + "cardReasonFallbacks=0";
    }

    private static final class CapturedLogs
            implements AutoCloseable {

        private final Logger logger =
                (Logger) LoggerFactory.getLogger(
                        AiReportGenerator.class
                );

        private final ListAppender<ILoggingEvent> appender =
                new ListAppender<>();

        private CapturedLogs() {
            appender.start();
            logger.addAppender(appender);
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
