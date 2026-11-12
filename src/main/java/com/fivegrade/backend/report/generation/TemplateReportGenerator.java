package com.fivegrade.backend.report.generation;

import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendationWarning;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 확인된 결과만 사용해 설명을 생성한다. 문구는 FE·기획 검토용이다. */
public final class TemplateReportGenerator implements ReportGenerator {

    @Override
    public GeneratedReport generate(
            BigDecimal displayAverage,
            RecommendationResult recommendation
    ) {
        Objects.requireNonNull(displayAverage);
        Objects.requireNonNull(recommendation);

        int count = recommendation.cards().size();
        String summary = "안녕하세요. 입력한 성적과 지원 방향을 바탕으로 분석 결과를 안내합니다.";
        String assessment = "입력한 과목의 5등급 기준 성적을 동일 비중으로 계산한 참고 평균은 "
                + displayAverage.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + "입니다. 대학별 입결의 반영교과·가중치·산출 방식과 달라 비교에 오차가 있을 수 있습니다.";

        // 같은 의미의 기본 안내와 경고가 같은 문장을 사용하도록 해 중복을 제거한다.
        Set<String> strategy = new LinkedHashSet<>();
        if (count == 0) {
            strategy.add(warningText(WarningCode.NO_CANDIDATES, count));
            strategy.add("입력한 희망 학과와 지원 옵션, 제공되는 자료 범위를 확인해 주세요.");
        } else {
            strategy.add("추천 순서는 추천 서비스가 정한 순서입니다. 카드별 구간과 자료 한계를 함께 확인해 주세요.");
            if (count < RecommendationResult.MAX_CARDS) {
                strategy.add(warningText(WarningCode.FEWER_THAN_SIX_CARDS, count));
            }
        }
        for (RecommendationWarning warning : recommendation.warnings()) {
            if (warning.rank() == null) {
                strategy.add(warningText(warning.code(), count));
            }
        }
        strategy.add("수능최저 충족 여부와 지원 자격은 최신 모집요강에서 별도로 확인해 주세요.");

        ConsultingReport report = new ConsultingReport(
                ReportSection.template(summary),
                ReportSection.template(assessment),
                ReportSection.template(String.join(" ", strategy)),
                ReportSection.template(
                        "이 결과는 지원 검토를 돕는 참고용 추정 자료이며 실제 합격 여부나 합격 확률을 보장하지 않습니다. "
                                + "최신 전형 정보를 확인하며 지원 계획을 세워 보세요."
                )
        );
        List<CardReason> reasons = recommendation.cards().stream()
                .map(card -> new CardReason(
                        card.rank(),
                        ReportSection.template(reason(card, recommendation.warnings(), count))
                ))
                .toList();
        return new GeneratedReport(report, reasons);
    }

    private String reason(
            RecommendedCard card,
            List<RecommendationWarning> warnings,
            int count
    ) {
        Set<String> sentences = new LinkedHashSet<>();
        sentences.add(card.universityName() + " " + card.departmentName()
                + "의 " + card.admissionTypeName() + " 후보입니다.");
        sentences.add(switch (card.band()) {
            case SAFE -> "추천 모델에서 안정 구간으로 분류했습니다.";
            case MATCH -> "추천 모델에서 적정 구간으로 분류했습니다.";
            case REACH -> "추천 모델에서 소신 구간으로 분류했습니다.";
            case FAR -> "추천 모델에서 현재 성적으로 접근하기 먼 구간으로 분류했습니다.";
            case OVER -> "추천 모델에서 성적 여유가 큰 구간으로 분류했습니다. 다른 지원 후보도 함께 검토해 주세요.";
            case UNRATED -> warningText(WarningCode.UNRATED_CARD, count);
        });
        sentences.add(switch (card.candidateKind()) {
            case DESIRED_MAJOR -> "희망 학과에 해당하는 후보입니다.";
            case SIMILAR_MAJOR -> warningText(WarningCode.SIMILAR_MAJOR_USED, count);
            case OTHER_TRACK -> warningText(WarningCode.OTHER_TRACK_USED, count);
            case UNDECLARED -> warningText(WarningCode.UNDECLARED_USED, count);
        });
        if (Boolean.TRUE.equals(card.evidence().newOrMergedDepartment())) {
            sentences.add("신설·통합 모집단위로 입결이 변동할 수 있습니다.");
        }
        for (RecommendationWarning warning : warnings) {
            if (Objects.equals(warning.rank(), card.rank())) {
                sentences.add(warningText(warning.code(), count));
            }
        }
        sentences.add("최신 모집요강과 지원 자격을 확인해 주세요.");
        return String.join(" ", sentences);
    }

    /** 경고 코드별 안내 문구. 리포트 문장과 응답의 warnings.message가 같은 문구를 쓰도록 공개한다. */
    public static String warningText(WarningCode code, int count) {
        return switch (code) {
            case NO_CANDIDATES -> "현재 조건과 자료 범위에서 추천 가능한 후보가 없습니다.";
            case FEWER_THAN_SIX_CARDS -> "추천 가능한 후보가 최대 "
                    + RecommendationResult.MAX_CARDS + "장보다 적어 " + count + "장을 안내합니다.";
            case REACH_CARD_NOT_AVAILABLE -> "소신 카드 포함을 요청했지만 해당 구간 후보를 포함하지 못했습니다.";
            case SIMILAR_MAJOR_USED -> "희망 학과와 비슷한 학과로 범위를 넓힌 후보입니다.";
            case OTHER_TRACK_USED -> "선호 전형과 다른 전형으로 범위를 넓힌 후보입니다.";
            case UNDECLARED_USED -> "자율전공·무전공 등 학과를 정하지 않고 선발하는 후보입니다. "
                    + "입학 후 전공 선택 방식과 제한을 확인해 주세요.";
            case UNRATED_CARD -> "사용할 입결 컷이 없어 구간을 평가하지 못했습니다.";
            case LOW_CONFIDENCE_CARD -> "추천 서비스가 근거 자료가 제한적인 후보로 안내했습니다. 해석에 주의해 주세요.";
        };
    }
}
