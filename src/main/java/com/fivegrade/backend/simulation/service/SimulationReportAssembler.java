package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.report.generation.ReportGenerator;
import com.fivegrade.backend.report.generation.ReportGenerator.GeneratedReport;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import com.fivegrade.backend.simulation.dto.SimulationReportResult.ExplainedCard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 분석 결과에 설명을 연결한다. 계산·추천·저장·HTTP 처리는 수행하지 않는다. */
public final class SimulationReportAssembler {

    private final ReportGenerator generator;

    public SimulationReportAssembler(ReportGenerator generator) {
        this.generator = Objects.requireNonNull(generator);
    }

    public SimulationReportResult assemble(SimulationAnalysis analysis) {
        Objects.requireNonNull(analysis);
        var result = Objects.requireNonNull(analysis.recommendation());
        validateWarningRanks(result);
        GeneratedReport generated = Objects.requireNonNull(
                generator.generate(analysis.displayAverage(), result)
        );
        if (generated.reasons().size() != result.cards().size()) {
            throw new IllegalStateException("report reason count mismatch");
        }
        List<ExplainedCard> cards = new ArrayList<>();
        for (int i = 0; i < result.cards().size(); i++) {
            var card = result.cards().get(i);
            var reason = generated.reasons().get(i);
            if (reason.rank() != card.rank()) {
                throw new IllegalStateException("report reason rank mismatch");
            }
            cards.add(new ExplainedCard(card, reason.reason()));
        }
        return new SimulationReportResult(
                analysis.displayAverage(),
                analysis.subjects(),
                cards,
                result.warnings(),
                generated.report()
        );
    }

    /** 추천 결과의 불일치를 설명 생성 전에 거부한다. */
    private void validateWarningRanks(RecommendationResult result) {
        Set<Integer> ranks = new HashSet<>();
        for (var card : result.cards()) {
            ranks.add(card.rank());
        }
        for (var warning : result.warnings()) {
            if (warning.rank() != null && !ranks.contains(warning.rank())) {
                throw new IllegalStateException("warning rank does not refer to an existing card");
            }
        }
    }
}
