package com.fivegrade.backend.report.generation;

import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 최소 설명 자료만 전달하고 검증을 통과한 섹션만 AI 문장으로 교체한다. */
public final class AiReportGenerator implements ReportGenerator {

    private static final List<String> SECTION_IDS = List.of(
            "greetingSummary", "overallAssessment", "applicationStrategy", "closing");
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?");
    private static final Pattern NAME = Pattern.compile(
            "[가-힣A-Za-z0-9·]+(?:대학교|대학|학과|학부|전형|대)");
    private static final Pattern FORBIDDEN = Pattern.compile(
            "https?://|www\\.|@|[%％]|퍼센트|합격률|합격확률|합격보장|합격확정|"
                    + "보장|무조건|반드시합격|틀림없이|확실히합격|원서순서변경|추천순서변경|"
                    + "경쟁률|모집인원|gradeGap|컷은|컷이|최저등급은",
            Pattern.CASE_INSENSITIVE);
    private final TemplateReportGenerator template = new TemplateReportGenerator();
    private final Client client;

    public AiReportGenerator(Client client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public GeneratedReport generate(BigDecimal displayAverage, RecommendationResult recommendation) {
        GeneratedReport baseline = template.generate(displayAverage, recommendation);
        Map<String, String> sources = sources(baseline);
        if (sources.values().stream().mapToInt(String::length).sum() > 16000) {
            return baseline;
        }
        Map<String, String> output;
        try {
            output = client.generate(Map.copyOf(sources));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return baseline;
        } catch (Exception unavailable) {
            // 예외 메시지·프롬프트·응답·성적은 기록하지 않는다.
            return baseline;
        }
        if (output == null || !sources.keySet().containsAll(output.keySet())) {
            return baseline;
        }
        Map<String, ReportSection> sections = new LinkedHashMap<>();
        sources.forEach((id, source) -> sections.put(id,
                validate(output.get(id), source)
                        ? ReportSection.llm(output.get(id).strip())
                        : ReportSection.template(source)));
        var report = new ConsultingReport(sections.get(SECTION_IDS.get(0)),
                sections.get(SECTION_IDS.get(1)), sections.get(SECTION_IDS.get(2)),
                sections.get(SECTION_IDS.get(3)));
        List<CardReason> reasons = baseline.reasons().stream()
                .map(reason -> new CardReason(reason.rank(), sections.get("card" + reason.rank())))
                .toList();
        return new GeneratedReport(report, reasons);
    }

    /** 과목 행·사용자·원본 요청·unitId·내부 점수는 포함하지 않는다. */
    private Map<String, String> sources(GeneratedReport baseline) {
        Map<String, String> result = new LinkedHashMap<>();
        var report = baseline.report();
        result.put("greetingSummary", report.greetingSummary().text());
        result.put("overallAssessment", report.overallAssessment().text());
        result.put("applicationStrategy", report.applicationStrategy().text());
        result.put("closing", report.closing().text());
        baseline.reasons().forEach(reason -> result.put("card" + reason.rank(), reason.reason().text()));
        return result;
    }

    /** 검증은 보수적인 문장 검사이며 모든 의미적 오류를 증명하는 검사는 아니다. */
    private boolean validate(String text, String source) {
        if (text == null || text.isBlank() || text.length() > 1400) {
            return false;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        if (!normalized.equals(text) || text.codePoints().anyMatch(c ->
                Character.getType(c) == Character.FORMAT || Character.isISOControl(c))) {
            return false;
        }
        // 숫자·고유명사·분류·경고·면책 문장을 그대로 보존한다.
        // AI는 이 사실 문장 사이의 연결과 안내 문장을 다듬는다.
        String additions = text;
        for (String sentence : source.split("(?<=[.!?])\\s+")) {
            if (!sentence.startsWith("최신 ") && !text.contains(sentence)) {
                return false;
            }
            additions = additions.replace(sentence, "");
        }
        if (FORBIDDEN.matcher(additions.replaceAll("\\s+", "")).find()) {
            return false;
        }
        if (Pattern.compile("합격|불합격|보장|확률|등급|평균|컷|경쟁률|모집|장학|등록금|"
                        + "안정|적정|소신|순위|순서|아니|잘못|오류|반대|무시|수정|정정")
                .matcher(additions.replaceAll("\\s+", "")).find()) {
            return false;
        }
        if (!tokens(NUMBER, text).equals(tokens(NUMBER, source))) {
            return false;
        }
        Set<String> names = Set.copyOf(tokens(NAME, source));
        if (!names.containsAll(tokens(NAME, text))) {
            return false;
        }
        // 분류가 추가로 등장하거나 반대 구간 표현이 섞이면 교체하지 않는다.
        for (String term : List.of("안정", "적정", "소신", "먼 구간", "여유가 큰", "평가하지 못")) {
            if (occurrences(text, term) != occurrences(source, term)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> tokens(Pattern pattern, String text) {
        List<String> values = new ArrayList<>();
        pattern.matcher(text).results().forEach(match -> values.add(match.group()));
        values.sort(String::compareTo);
        return values;
    }

    private static int occurrences(String text, String part) {
        return (text.length() - text.replace(part, "").length()) / part.length();
    }

    /** 요청당 한 번 호출한다. 구현체가 전체 응답 수신 시간과 동시 호출 수를 제한한다. */
    @FunctionalInterface
    public interface Client {
        Map<String, String> generate(Map<String, String> sources) throws Exception;
    }
}
