package com.fivegrade.backend.report.generation;

import com.fivegrade.backend.recommendation.dto.RecommendationResult;
import com.fivegrade.backend.recommendation.dto.RecommendedCard;
import com.fivegrade.backend.recommendation.dto.WarningCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 기획 확정 문구를 사용한다. 화면 안내는 공개 warnings와 FE에서 담당한다. */
public final class TemplateReportGenerator implements ReportGenerator {

    private static final String GREETING =
            "입력해 주신 성적과 지원 방향을 바탕으로 수시 원서 추천 리스트를 안내해 드려요.";

    private static final String AVERAGE_NOTICE =
            "입력한 과목의 5등급 기준 성적을 같은 비중으로 계산한 참고 평균이에요. "
                    + "대학별 입결의 반영 교과·가중치·산출 방식과 달라 비교에 오차가 있을 수 있어요.";

    private static final String CLOSING =
            "이 리포트는 과거 입결을 바탕으로 한 참고용 추정이에요. "
                    + "최신 모집요강을 꼭 확인하고, 최종 지원 결정은 직접 판단해 주세요.";

    @Override
    public GeneratedReport generate(
            BigDecimal displayAverage,
            RecommendationResult recommendation
    ) {
        Objects.requireNonNull(displayAverage);
        Objects.requireNonNull(recommendation);

        int count = recommendation.cards().size();

        String assessment = "참고 평균은 "
                + displayAverage.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + "이에요. "
                + AVERAGE_NOTICE;

        String strategy = count == 0
                ? warningText(WarningCode.NO_CANDIDATES, count)
                : "추천 후보별 지원 구간과 최신 모집요강을 함께 확인해 주세요. "
                + "수능최저 충족 여부와 지원 자격도 별도로 확인해 주세요.";

        // 부족·확장·소신 미포함 안내는 공개 warnings에서 한 번만 노출한다.
        ConsultingReport report = new ConsultingReport(
                ReportSection.template(GREETING),
                ReportSection.template(assessment),
                ReportSection.template(strategy),
                ReportSection.template(CLOSING)
        );

        List<CardReason> reasons = recommendation.cards().stream()
                .map(card -> new CardReason(
                        card.rank(),
                        ReportSection.template(reason(card))
                ))
                .toList();

        return new GeneratedReport(report, reasons);
    }

    private String reason(RecommendedCard card) {
        List<String> sentences = new ArrayList<>();

        sentences.add(
                card.universityName()
                        + " "
                        + card.departmentName()
                        + "의 "
                        + card.admissionTypeName()
                        + " 후보예요."
        );

        sentences.add(switch (card.band()) {
            case SAFE ->
                    "현재 성적과 과거 입결을 비교했을 때, 비교적 여유 있게 지원을 검토할 수 있는 후보예요.";
            case MATCH ->
                    "현재 성적이 과거 입결을 바탕으로 추정한 지원권과 비슷한 수준인 후보예요.";
            case REACH ->
                    "현재 성적보다 다소 높은 지원권의 후보로, 다른 지원 카드와 균형을 고려해 도전해 볼 수 있어요.";
            case FAR ->
                    "현재 성적과 추정 지원권의 차이가 커, 지원 여부를 신중하게 검토할 필요가 있는 후보예요.";
            case OVER ->
                    "현재 성적이 추정 지원권보다 충분히 앞서는 후보예요. 더 높은 지원권의 후보도 함께 검토해 보세요.";
            case UNRATED ->
                    warningText(WarningCode.UNRATED_CARD, 0);
        });

        if (Boolean.TRUE.equals(card.evidence().newOrMergedDepartment())) {
            sentences.add(
                    "신설·통합 모집단위로 입결이 변동할 수 있어요."
            );
        }

        // candidateKind와 경고는 원본 결과에 보존하고 여기서 반복 설명하지 않는다.
        sentences.add(
                "최신 모집요강과 지원 자격을 확인해 주세요."
        );

        return String.join(" ", sentences);
    }

    /**
     * 공개 매퍼에서도 사용한다.
     * OTHER_TRACK_USED는 필터링하고 빈 문구를 노출하지 않는다.
     */
    public static String warningText(
            WarningCode code,
            int cardCount
    ) {
        Objects.requireNonNull(code);

        return switch (code) {
            case NO_CANDIDATES ->
                    "조건에 맞는 원서를 찾지 못했어요. 희망 학과나 선호 전형을 바꿔서 다시 시도해 주세요.";

            case FEWER_THAN_SIX_CARDS ->
                    "현재 조건에서 추천 가능한 후보는 "
                            + cardCount
                            + "장이에요.";

            case REACH_CARD_NOT_AVAILABLE ->
                    "요청하신 소신 카드는 조건에 맞는 후보가 없어 포함하지 못했어요.";

            case SIMILAR_MAJOR_USED ->
                    "일부 후보는 희망 학과와 비슷한 학과로 넓혀 찾았어요.";

            case OTHER_TRACK_USED ->
                    "";

            case UNDECLARED_USED ->
                    "자율전공·무전공 후보는 학과를 정하지 않고 뽑는 모집단위예요. "
                            + "입학 후 전공 선택 방식을 확인해 주세요.";

            case UNRATED_CARD ->
                    "지원 수준을 판단할 입결 자료가 부족해요. 최신 모집요강과 대학의 입시 결과를 확인해 주세요.";

            case LOW_CONFIDENCE_CARD ->
                    "추천 근거가 되는 입결 자료가 적어 신뢰도가 다소 낮으니 결과 해석에 주의해 주세요";
        };
    }
}
