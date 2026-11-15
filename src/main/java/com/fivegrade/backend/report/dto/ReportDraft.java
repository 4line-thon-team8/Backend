package com.fivegrade.backend.report.dto;

import java.util.List;

/**
 * 저장할 리포트의 최소 항목.
 *
 * @param greetingSummary 인사 요약 문장. 없으면 null
 * @param recommendations 추천 원서 1~6장
 */
public record ReportDraft(String greetingSummary, List<RecommendationDraft> recommendations) {

}
