package com.fivegrade.backend.simulation.service;

import com.fivegrade.backend.admission.entity.Major;
import com.fivegrade.backend.admission.repository.MajorRepository;
import com.fivegrade.backend.common.error.BusinessException;
import com.fivegrade.backend.common.error.ErrorCode;
import com.fivegrade.backend.holistic.report.HolisticNarrative;
import com.fivegrade.backend.holistic.report.HolisticNarrativeFactsFactory.DesiredMajor;
import com.fivegrade.backend.holistic.report.HolisticNarrativeService;
import com.fivegrade.backend.holistic.service.HolisticEvaluation;
import com.fivegrade.backend.simulation.dto.SimulationAnalysis;
import com.fivegrade.backend.simulation.dto.SimulationReportResult;
import com.fivegrade.backend.simulation.dto.SimulationRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 학종 서술을 별도 작업으로 시작하고 기존 리포트는 요청 스레드에서 생성한다.
 * 두 생성 작업은 겹쳐 실행된다. 서술 전체 시간 초과·실행 거절은 빈 결과로 대신한다.
 * 기존 리포트 생성·계산·학과 조회 오류는 숨기지 않는다.
 */
@Service
public class SimulationReportCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(SimulationReportCoordinator.class);
    private final SimulationReportAssembler assembler;
    private final HolisticNarrativeService narratives;
    private final MajorRepository majors;
    private final ExecutorService executor;
    private final long timeoutMillis;

    public SimulationReportCoordinator(
            SimulationReportAssembler assembler,
            HolisticNarrativeService narratives,
            MajorRepository majors,
            @Qualifier("holisticNarrativeExecutor") ExecutorService executor,
            @Value("${HOLISTIC_NARRATIVE_TOTAL_TIMEOUT_MS:23000}") long timeoutMillis
    ) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("narrative timeout must be positive");
        }
        this.assembler = assembler;
        this.narratives = narratives;
        this.majors = majors;
        this.executor = executor;
        this.timeoutMillis = timeoutMillis;
    }

    /** 서술 결과는 저장용 내부 DTO에 넣지 않고 응답 변환 단계로 별도 전달한다. */
    public record GeneratedReport(SimulationReportResult result, HolisticNarrative narrative) {
        public GeneratedReport {
            Objects.requireNonNull(result);
            Objects.requireNonNull(narrative);
        }
    }

    public GeneratedReport assemble(SimulationAnalysis analysis, SimulationRequest request) {
        if (!(analysis.holistic() instanceof HolisticEvaluation.Applied applied)) {
            return new GeneratedReport(assembler.assemble(analysis), HolisticNarrative.empty());
        }
        // DB 조회 순서에 의존하지 않고, 검증된 선택 코드 순서로 일반 값만 준비한다.
        List<DesiredMajor> desired = resolveDesiredMajors(request.applicationPreference().desiredMajorCodes());
        CompletableFuture<HolisticNarrative> completion = new CompletableFuture<>();
        Future<?> task;
        try {
            task = executor.submit(() -> {
                try {
                    completion.complete(narratives.narrate(applied, request.holisticAnalysis(), desired));
                } catch (RuntimeException failure) {
                    completion.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            LOG.warn("event=holistic_narrative_task_rejected");
            return new GeneratedReport(assembler.assemble(analysis), HolisticNarrative.empty());
        }
        // 학종 작업 시작 시점부터 제한한다.
        CompletableFuture<HolisticNarrative> bounded = completion
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .handle((value, failure) -> {
                    if (failure == null && value != null) {
                        return value;
                    }
                    LOG.warn("event={}", failure instanceof TimeoutException
                            ? "holistic_narrative_task_timeout" : "holistic_narrative_task_failed");
                    task.cancel(true);
                    return HolisticNarrative.empty();
                });
        try {
            SimulationReportResult report = assembler.assemble(analysis);
            return new GeneratedReport(report, bounded.get());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("report assembly interrupted", failure);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("narrative result unavailable", failure);
        } finally {
            if (!completion.isDone()) {
                completion.cancel(false);
            }
            task.cancel(true);
        }
    }

    private List<DesiredMajor> resolveDesiredMajors(List<String> codes) {
        Map<String, Major> byCode = majors.findAllById(codes).stream()
                .collect(Collectors.toMap(Major::getCode, Function.identity()));
        return codes.stream().distinct().map(code -> {
            Major major = byCode.get(code);
            if (major == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "입력값이 올바르지 않습니다: applicationPreference.desiredMajorCodes");
            }
            return new DesiredMajor(major.getCode(), major.getName());
        }).toList();
    }
}
