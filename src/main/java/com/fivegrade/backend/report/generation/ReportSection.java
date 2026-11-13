package com.fivegrade.backend.report.generation;

/** 설명 문장과 해당 섹션의 실제 생성 방식을 담는다. */
public record ReportSection(String text, String generationMethod) {

    public ReportSection {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("report text is required");
        }
        if (!"template".equals(generationMethod) && !"llm".equals(generationMethod)) {
            throw new IllegalArgumentException("unsupported generation method");
        }
    }

    public static ReportSection template(String text) {
        return new ReportSection(text, "template");
    }

    public static ReportSection llm(String text) {
        return new ReportSection(text, "llm");
    }
}

