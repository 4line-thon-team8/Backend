package com.fivegrade.backend.report.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fivegrade.backend.recommendation.dto.CandidateKind;
import com.fivegrade.backend.recommendation.dto.CardEvidence;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.probability.AdmissionBand;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiReportCompatibilityTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "기계ㆍ시스템디자인공학과",
            "융합Ⅱ학부"
    })
    void preservesCompatibilityCharactersAlreadyPresentInSource(
            String department
    ) {
        var result = new AiReportGenerator(sources -> {
            var output = new LinkedHashMap<String, String>();

            sources.forEach((id, source) ->
                    output.put(
                            id,
                            source + " 차분히 검토해 보세요."
                    )
            );

            return output;
        }).generate(
                BigDecimal.ONE,
                recommendation(department)
        );

        assertEquals(
                "llm",
                result.reasons().getFirst()
                        .reason().generationMethod()
        );
    }

    @Test
    void rejectsCompatibilityCharactersAddedByAiOnlyForThatSlot() {
        var input = recommendation(
                "기계ㆍ시스템디자인공학과"
        );

        var baseline = new TemplateReportGenerator()
                .generate(BigDecimal.ONE, input);

        var result = new AiReportGenerator(sources -> {
            var output = new LinkedHashMap<>(sources);

            output.put(
                    "card1",
                    sources.get("card1") + " Ａ 안내예요."
            );

            return output;
        }).generate(BigDecimal.ONE, input);

        assertEquals(baseline.reasons(), result.reasons());

        assertEquals(
                "llm",
                result.report().closing().generationMethod()
        );
    }

    @Test
    void preservesApprovedClosingIncludingLatestAdmissionsGuide() {
        var baseline = new TemplateReportGenerator().generate(
                BigDecimal.ONE,
                RecommendationResult.empty()
        );

        var result = new AiReportGenerator(
                sources -> new LinkedHashMap<>(sources)
        ).generate(
                BigDecimal.ONE,
                RecommendationResult.empty()
        );

        assertEquals(
                "llm",
                result.report().closing().generationMethod()
        );
        assertEquals(
                baseline.report().closing().text(),
                result.report().closing().text()
        );
    }

    @Test
    void cannotDeleteLatestAdviceFromApprovedClosing() {
        var input = RecommendationResult.empty();

        var baseline = new TemplateReportGenerator()
                .generate(BigDecimal.ONE, input);

        var result = new AiReportGenerator(sources -> {
            var output = new LinkedHashMap<>(sources);

            output.put(
                    "closing",
                    sources.get("closing").replace(
                            "최신 모집요강을 꼭 확인하고, 최종 지원 결정은 직접 판단해 주세요.",
                            ""
                    )
            );

            return output;
        }).generate(BigDecimal.ONE, input);

        assertEquals(
                baseline.report().closing(),
                result.report().closing()
        );
    }

    private static RecommendationResult recommendation(
            String department
    ) {
        return new RecommendationResult(
                List.of(
                        new RecommendedCard(
                                1,
                                1L,
                                "가상대학",
                                department,
                                "학생부교과전형",
                                AdmissionBand.MATCH,
                                CandidateKind.DESIRED_MAJOR,
                                0.1,
                                new CardEvidence(
                                        null,
                                        null,
                                        null,
                                        0
                                )
                        )
                ),
                List.of()
        );
    }
}
