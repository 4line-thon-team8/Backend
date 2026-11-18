package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.admission.entity.Major;
import com.fivegrade.backend.admission.repository.MajorRepository;
import com.fivegrade.backend.holistic.report.HolisticNarrative;
import com.fivegrade.backend.holistic.report.HolisticNarrativeService;
import com.fivegrade.backend.holistic.scoring.DocumentContext;
import com.fivegrade.backend.holistic.scoring.DocumentLevelCalculator.ElementLevels;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.report.generation.TemplateReportGenerator;
import com.fivegrade.backend.simulation.dto.ApplicationPreference;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulationReportCoordinatorTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HolisticNarrativeService narratives = mock(HolisticNarrativeService.class);
    private final MajorRepository majors = mock(MajorRepository.class);
    private final SimulationReportAssembler assembler = mock(SimulationReportAssembler.class);
    private final SimulationReportAssembler real = new SimulationReportAssembler(new TemplateReportGenerator());

    @AfterEach
    void close() throws Exception {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
    }

    @Test
    void skippedStatesDoNotCallNarrativeOrLoadMajors() {
        for (var reason : HolisticEvaluation.SkipReason.values()) {
            var analysis = analysis(new HolisticEvaluation.Skipped(reason));
            when(assembler.assemble(analysis)).thenReturn(real.assemble(analysis));
            var result = coordinator(3000).assemble(analysis, request());
            assertEquals(HolisticNarrative.empty(), result.narrative());
        }
        verifyNoInteractions(narratives, majors);
    }

    @Test
    void runsInParallelAndKeepsDesiredOrderAndMapsNarrative() throws Exception {
        var applied = applied();
        var analysis = analysis(applied);
        var loaded = List.of(major("CS", "컴퓨터공학과"), major("STAT", "통계학과"));
        when(majors.findAllById(any())).thenReturn(loaded);
        var started = new CountDownLatch(1);
        var reportStarted = new CountDownLatch(1);
        var text = HolisticNarrative.Text.llm("관심 분야를 탐색했어요.");
        var narrative = new HolisticNarrative(text, List.of(
                new HolisticNarrative.MajorAdvice("STAT", "통계학과", HolisticNarrative.Text.template("자료 해석 과정을 정리해 보세요.")),
                new HolisticNarrative.MajorAdvice("CS", "컴퓨터공학과", text)),
                new HolisticNarrative.Detail(text, List.of(text), List.of(), List.of(text)));
        when(narratives.narrate(eq(applied), isNull(), anyList())).thenAnswer(call -> {
            started.countDown();
            assertTrue(reportStarted.await(3, TimeUnit.SECONDS));
            var desired = call.<List<com.fivegrade.backend.holistic.report.HolisticNarrativeFactsFactory.DesiredMajor>>getArgument(2);
            assertEquals(List.of("STAT", "CS"), desired.stream().map(d -> d.code()).toList());
            assertEquals(List.of("통계학과", "컴퓨터공학과"), desired.stream().map(d -> d.name()).toList());
            return narrative;
        });
        when(assembler.assemble(analysis)).thenAnswer(call -> {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            reportStarted.countDown();
            return real.assemble(analysis);
        });
        var result = coordinator(5000).assemble(analysis, request());
        assertEquals(narrative, result.narrative());
        var response = SimulationResponseMapper.toResponse(result,
                new SimulationReportSaver.SaveOutcome(SimulationReportSaver.Status.SKIPPED_NO_CARDS, null));
        assertEquals("LLM", response.holistic().activitySummary().generationMethod());
        assertEquals(List.of("통계학과", "컴퓨터공학과"), response.holistic().majorAdvices().stream().map(a -> a.department()).toList());
        assertEquals("TEMPLATE", response.holistic().majorAdvices().getFirst().advice().generationMethod());
        assertEquals(1, response.holistic().strengths().size());
        assertEquals(text.text(), response.holistic().detail().overview().text());
        assertEquals("LLM", response.holistic().detail().nextSteps().getFirst().generationMethod());
        assertNull(SimulationReportSaver.toDraft(result).greetingSummary());
        verify(narratives).narrate(eq(applied), isNull(), anyList());
    }

    @Test
    void overallTimeoutReturnsEmptyAndInterruptsWorker() throws Exception {
        var analysis = prepare();
        var interrupted = new CountDownLatch(1);
        when(narratives.narrate(any(), any(), anyList())).thenAnswer(call -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException failure) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return HolisticNarrative.empty();
        });
        var result = assertTimeoutPreemptively(java.time.Duration.ofSeconds(3),
                () -> coordinator(100).assemble(analysis, request()));
        assertEquals(HolisticNarrative.empty(), result.narrative());
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
    }

    @Test
    void rejectedTaskReturnsExistingReport() {
        var analysis = prepare();
        executor.shutdown();
        assertEquals(HolisticNarrative.empty(), coordinator(1000).assemble(analysis, request()).narrative());
        verifyNoInteractions(narratives);
    }

    @Test
    void unexpectedNarrativeFailureReturnsEmpty() {
        var analysis = prepare();
        when(narratives.narrate(any(), any(), anyList())).thenThrow(new IllegalStateException("test"));
        assertEquals(HolisticNarrative.empty(), coordinator(1000).assemble(analysis, request()).narrative());
    }

    @Test
    void existingReportFailureStillPropagates() {
        var analysis = prepare();
        when(narratives.narrate(any(), any(), anyList())).thenReturn(HolisticNarrative.empty());
        when(assembler.assemble(analysis)).thenThrow(new IllegalStateException("report failure"));
        assertThrows(IllegalStateException.class, () -> coordinator(1000).assemble(analysis, request()));
    }

    @Test
    void majorLookupFailureStillPropagates() {
        when(majors.findAllById(any())).thenThrow(new IllegalStateException("db failure"));
        assertThrows(IllegalStateException.class, () -> coordinator(1000).assemble(analysis(applied()), request()));
        verifyNoInteractions(narratives, assembler);
    }

    private SimulationAnalysis prepare() {
        var analysis = analysis(applied());
        var loaded = List.of(major("STAT", "통계학과"), major("CS", "컴퓨터공학과"));
        when(majors.findAllById(any())).thenReturn(loaded);
        when(assembler.assemble(analysis)).thenReturn(real.assemble(analysis));
        return analysis;
    }

    private SimulationReportCoordinator coordinator(long timeout) {
        return new SimulationReportCoordinator(assembler, narratives, majors, executor, timeout);
    }

    private Major major(String code, String name) {
        var major = mock(Major.class);
        when(major.getCode()).thenReturn(code);
        when(major.getName()).thenReturn(name);
        return major;
    }

    private SimulationRequest request() {
        var preference = mock(ApplicationPreference.class);
        when(preference.desiredMajorCodes()).thenReturn(List.of("STAT", "CS"));
        return new SimulationRequest(List.of(), preference, true, null);
    }

    private HolisticEvaluation.Applied applied() {
        return new HolisticEvaluation.Applied("0.7.0", Map.of("A3", 4.0), Map.of("ACADEMIC", 4.0),
                new DocumentContext(new ElementLevels(4.0, 3.0, 3.0), 3.3));
    }

    private SimulationAnalysis analysis(HolisticEvaluation evaluation) {
        return new SimulationAnalysis(new BigDecimal("2.00"), List.of(), RecommendationResult.empty(), evaluation);
    }
}
