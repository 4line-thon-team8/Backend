package com.fivegrade.backend.conversion.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 사용자 5등급제 숫자 성적의 단순 평균을 계산한다.
 *
 * 계산 대상 과목 선정과 숫자 등급 추출은 호출하는 계층의 책임이다.
 * 이 계산기는 전달된 모든 등급을 동일한 비중으로 반영한다.
 *
 * 학점·학년·학기 가중치나 대학별 반영 방식을 적용하지 않는다.
 * 과거 9등급 입결을 환산하는 기능도 포함하지 않는다.
 *
 * Spring과 DB에 의존하지 않는 순수 Java 계산기다.
 */
public final class FiveGradeAverageCalculator {

    private static final BigDecimal MIN_GRADE =
            BigDecimal.ONE;

    private static final BigDecimal MAX_GRADE =
            BigDecimal.valueOf(5);

    private static final int DISPLAY_SCALE = 2;

    /**
     * 입력 등급의 단순 평균을 최종 표시값으로 계산한다.
     *
     * 합산 중에는 반올림하지 않고,
     * 마지막 나눗셈에서만 소수 둘째 자리로 HALF_UP 반올림한다.
     *
     * 반환값은 표시용 평균이다.
     * 추천 비교에 이 값을 사용할지는 비교 정밀도 합의 후 결정한다.
     *
     * 잘못된 원소를 조용히 제외하지 않으며,
     * 예외 메시지에 실제 입력 성적을 포함하지 않는다.
     */
    public BigDecimal calculateForDisplay(
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

        return total.divide(
                BigDecimal.valueOf(grades.size()),
                DISPLAY_SCALE,
                RoundingMode.HALF_UP
        );
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