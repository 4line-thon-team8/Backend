package com.fivegrade.backend.conversion.calculation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * 사용자 5등급 숫자 성적의 단순 평균을 계산한다.
 *
 * 계산 대상 과목 선정과 숫자 등급 추출은 호출하는 계층의 책임이다.
 * 모든 등급을 동일한 비중으로 반영하며 가중치를 적용하지 않는다.
 *
 * 과거 9등급 입결의 참고 환산은 담당하지 않는다.
 */
public final class FiveGradeAverageCalculator {

    private static final BigDecimal MIN_GRADE = BigDecimal.ONE;
    private static final BigDecimal MAX_GRADE = BigDecimal.valueOf(5);

    private static final int DISPLAY_SCALE = 2;

    private static final MathContext COMPARISON_CONTEXT =
            MathContext.DECIMAL128;

    /**
     * 추천 비교용 평균을 계산한다.
     *
     * 합산은 정확히 수행하고 마지막 나눗셈에만
     * DECIMAL128의 유효 숫자 34자리 정밀도를 적용한다.
     *
     * 화면 표시를 위한 소수 둘째 자리 반올림은 하지 않는다.
     * 반복소수에는 유한 정밀도가 필요하므로 무한 정밀도 값은 아니다.
     */
    public BigDecimal calculateForComparison(
            List<BigDecimal> grades
    ) {
        BigDecimal total = sumValidatedGrades(grades);

        return total.divide(
                BigDecimal.valueOf(grades.size()),
                COMPARISON_CONTEXT
        );
    }

    /**
     * 화면 표시용 평균을 계산한다.
     *
     * 합계에서 직접 나누고 소수 둘째 자리로 HALF_UP 반올림한다.
     * 비교용 평균을 다시 반올림해서 생기는 이중 반올림을 피한다.
     */
    public BigDecimal calculateForDisplay(
            List<BigDecimal> grades
    ) {
        BigDecimal total = sumValidatedGrades(grades);

        return total.divide(
                BigDecimal.valueOf(grades.size()),
                DISPLAY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal sumValidatedGrades(
            List<BigDecimal> grades
    ) {
        if (grades == null || grades.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one numeric grade is required"
            );
        }

        BigDecimal total = BigDecimal.ZERO;

        for (BigDecimal grade : grades) {
            validateGrade(grade);
            total = total.add(grade);
        }

        return total;
    }

    private void validateGrade(BigDecimal grade) {
        if (grade == null) {
            throw new IllegalArgumentException(
                    "numeric grade is required"
            );
        }

        if (grade.compareTo(MIN_GRADE) < 0
                || grade.compareTo(MAX_GRADE) > 0) {
            throw new IllegalArgumentException(
                    "numeric grade is out of range"
            );
        }
    }
}