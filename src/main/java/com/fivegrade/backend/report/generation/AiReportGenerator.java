package com.fivegrade.backend.report.generation;

import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 최소 설명 자료만 전달하고 검증을 통과한 섹션만 AI 문장으로 교체한다. */
public final class AiReportGenerator implements ReportGenerator {

    private static final Logger LOG =
            LoggerFactory.getLogger(AiReportGenerator.class);

    private static final List<String> SECTION_IDS = List.of(
            "greetingSummary",
            "overallAssessment",
            "applicationStrategy",
            "closing"
    );

    private static final Pattern NUMBER =
            Pattern.compile("[+-]?\\d+(?:\\.\\d+)?");

    private static final Pattern NAME = Pattern.compile(
            "[가-힣A-Za-z0-9·]+(?:대학교|대학|학과|학부|전형|대)"
    );

    private static final Pattern FORBIDDEN = Pattern.compile(
            "https?://|www\\.|@|[%％]|퍼센트|합격률|합격확률|합격보장|합격확정|"
                    + "보장|무조건|반드시합격|틀림없이|확실히합격|원서순서변경|추천순서변경|"
                    + "경쟁률|모집인원|gradeGap|컷은|컷이|최저등급은",
            Pattern.CASE_INSENSITIVE
    );

    private final TemplateReportGenerator template =
            new TemplateReportGenerator();

    private final Client client;

    public AiReportGenerator(Client client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public GeneratedReport generate(
            BigDecimal displayAverage,
            RecommendationResult recommendation
    ) {
        GeneratedReport baseline =
                template.generate(displayAverage, recommendation);

        Map<String, String> sources = sources(baseline);

        int totalSourceLength = sources.values().stream()
                .mapToInt(String::length)
                .sum();

        if (totalSourceLength > 16000) {
            recordFallback(
                    AiReportFailure.Reason.SOURCE_TOO_LARGE,
                    sources.keySet(),
                    sources.size()
            );
            return baseline;
        }

        Map<String, String> output;

        try {
            output = client.generate(Map.copyOf(sources));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();

            recordFallback(
                    AiReportFailure.Reason.INTERRUPTED,
                    sources.keySet(),
                    sources.size()
            );
            return baseline;
        } catch (Exception unavailable) {
            AiReportFailure.Reason reason =
                    AiReportFailure.reasonOf(unavailable);

            if (reason == AiReportFailure.Reason.INTERRUPTED) {
                Thread.currentThread().interrupt();
            }

            recordFallback(
                    reason,
                    sources.keySet(),
                    sources.size()
            );
            return baseline;
        }

        if (output == null
                || !sources.keySet().containsAll(output.keySet())) {
            recordFallback(
                    AiReportFailure.Reason.INVALID_RESPONSE,
                    sources.keySet(),
                    sources.size()
            );
            return baseline;
        }

        Map<String, ReportSection> sections = new LinkedHashMap<>();
        Set<String> rejected = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String id = entry.getKey();
            String source = entry.getValue();
            String generated = output.get(id);

            if (validate(generated, source)) {
                sections.put(
                        id,
                        ReportSection.llm(generated.strip())
                );
            } else {
                sections.put(
                        id,
                        ReportSection.template(source)
                );
                rejected.add(id);
            }
        }

        if (!rejected.isEmpty()) {
            recordFallback(
                    AiReportFailure.Reason.VALIDATION_REJECTED,
                    rejected,
                    sources.size()
            );
        }

        ConsultingReport report = new ConsultingReport(
                sections.get(SECTION_IDS.get(0)),
                sections.get(SECTION_IDS.get(1)),
                sections.get(SECTION_IDS.get(2)),
                sections.get(SECTION_IDS.get(3))
        );

        List<CardReason> reasons = baseline.reasons().stream()
                .map(reason -> new CardReason(
                        reason.rank(),
                        sections.get("card" + reason.rank())
                ))
                .toList();

        return new GeneratedReport(report, reasons);
    }

    /** 고정 코드와 대체 개수만 기록한다. 문장·평균·예외 원문은 기록하지 않는다. */
    private static void recordFallback(
            AiReportFailure.Reason reason,
            Set<String> fallbackIds,
            int totalSections
    ) {
        long cardFallbacks = fallbackIds.stream()
                .filter(id -> id.startsWith("card"))
                .count();

        LOG.warn(
                "event=report_ai_fallback reason={} totalSections={} fallbackSections={} "
                        + "greetingSummaryFallback={} overallAssessmentFallback={} "
                        + "applicationStrategyFallback={} closingFallback={} cardReasonFallbacks={}",
                reason.name(),
                totalSections,
                fallbackIds.size(),
                fallbackIds.contains("greetingSummary") ? 1 : 0,
                fallbackIds.contains("overallAssessment") ? 1 : 0,
                fallbackIds.contains("applicationStrategy") ? 1 : 0,
                fallbackIds.contains("closing") ? 1 : 0,
                cardFallbacks
        );
    }

    /**
     * 참고 평균을 포함한 설명 자료를 전달한다.
     * 과목 행·사용자·원본 요청·인증정보는 포함하지 않는다.
     */
    private Map<String, String> sources(GeneratedReport baseline) {
        Map<String, String> result = new LinkedHashMap<>();

        ConsultingReport report = baseline.report();

        result.put(
                "greetingSummary",
                report.greetingSummary().text()
        );
        result.put(
                "overallAssessment",
                report.overallAssessment().text()
        );
        result.put(
                "applicationStrategy",
                report.applicationStrategy().text()
        );
        result.put(
                "closing",
                report.closing().text()
        );

        baseline.reasons().forEach(reason ->
                result.put(
                        "card" + reason.rank(),
                        reason.reason().text()
                )
        );

        return result;
    }

    /** 보수적인 문장 검사이며 모든 의미적 오류를 증명하는 검사는 아니다. */
    private boolean validate(String text, String source) {
        if (text == null || text.isBlank() || text.length() > 1400) {
            return false;
        }

        if (text.codePoints().anyMatch(codePoint ->
                Character.getType(codePoint) == Character.FORMAT
                        || Character.isISOControl(codePoint))) {
            return false;
        }

        /*
         * 템플릿이 문구의 단일 기준이다.
         * 구간 이름이나 "최신 " 접두어를 별도로 판정하지 않는다.
         * 기획 확정 문장은 유지하고 AI가 추가한 연결 문장만 검사한다.
         */
        String additions = text;

        for (String sentence : source.split("(?<=[.!?])\\s+")) {
            if (!text.contains(sentence)) {
                return false;
            }

            additions = additions.replace(sentence, "");
        }

        /*
         * 원문 학과명의 ㆍ, Ⅱ 등은 그대로 허용한다.
         * AI가 새로 추가한 부분에만 NFKC 검사를 적용한다.
         */
        if (!Normalizer.normalize(
                additions,
                Normalizer.Form.NFKC
        ).equals(additions)) {
            return false;
        }

        String compactAdditions =
                additions.replaceAll("\\s+", "");

        if (FORBIDDEN.matcher(compactAdditions).find()) {
            return false;
        }

        if (Pattern.compile(
                "합격|불합격|보장|확률|등급|평균|컷|경쟁률|모집인원|모집정원|장학|등록금|"
                        + "안정|적정|소신|순위|순서|아니|잘못|오류|반대|무시|수정|정정"
        ).matcher(compactAdditions).find()) {
            return false;
        }

        if (!tokens(NUMBER, text).equals(tokens(NUMBER, source))) {
            return false;
        }

        Set<String> names = Set.copyOf(tokens(NAME, source));

        if (!names.containsAll(tokens(NAME, text))) {
            return false;
        }

        return true;
    }

    private static List<String> tokens(
            Pattern pattern,
            String text
    ) {
        List<String> values = new ArrayList<>();

        pattern.matcher(text).results()
                .forEach(match -> values.add(match.group()));

        values.sort(String::compareTo);

        return values;
    }

    /**
     * 요청당 한 번 호출한다.
     * 구현체가 전체 응답 수신 시간과 동시 호출 수를 제한한다.
     */
    @FunctionalInterface
    public interface Client {

        Map<String, String> generate(
                Map<String, String> sources
        ) throws Exception;
    }
}
