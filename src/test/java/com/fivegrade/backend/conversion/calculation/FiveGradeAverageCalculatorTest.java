package com.fivegrade.backend.conversion.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DB나 Spring 실행 없이 사용자 성적의 단순 평균을 검증한다.
 *
 * 정상 계산, 등급 경계, 반올림, 잘못된 입력 거부,
 * 입력 목록 보존을 확인한다.
 */
class FiveGradeAverageCalculatorTest {

    private final FiveGradeAverageCalculator calculator =
            new FiveGradeAverageCalculator();

    @Test
    void preservesSingleGrade() {
        assertEquals(
                new BigDecimal("3.00"),
                calculator.calculateForDisplay(
                        grades("3")
                )
        );
    }

    @Test
    void calculatesSimpleAverage() {
        assertEquals(
                new BigDecimal("2.00"),
                calculator.calculateForDisplay(
                        grades("1", "2", "3")
                )
        );
    }

    @Test
    void roundsRepeatingDecimalAtFinalDivision() {
        assertEquals(
                new BigDecimal("1.67"),
                calculator.calculateForDisplay(
                        grades("1", "2", "2")
                )
        );
    }

    @Test
    void roundsHalfUpAtThirdDecimalPlace() {
        // 9 / 8 = 1.125이므로 HALF_UP 결과는 1.13이다.
        assertEquals(
                new BigDecimal("1.13"),
                calculator.calculateForDisplay(
                        grades(
                                "1", "1", "1", "1",
                                "1", "1", "1", "2"
                        )
                )
        );
    }

    @Test
    void acceptsBothGradeBoundaries() {
        assertEquals(
                new BigDecimal("1.00"),
                calculator.calculateForDisplay(
                        grades("1", "1")
                )
        );

        assertEquals(
                new BigDecimal("5.00"),
                calculator.calculateForDisplay(
                        grades("5", "5")
                )
        );
    }

    @Test
    void doesNotRoundEachInputBeforeAveraging() {
        // 내부 계산기가 입력 소수를 먼저 반올림하지 않는지 검증한다.
        // 외부 API의 소수 등급 허용 정책을 정하는 테스트는 아니다.
        assertEquals(
                new BigDecimal("1.01"),
                calculator.calculateForDisplay(
                        grades("1.004", "1.004", "1.014")
                )
        );
    }

    @Test
    void rejectsNullOrEmptyList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateForDisplay(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateForDisplay(
                        List.of()
                )
        );
    }

    @Test
    void rejectsNullElementInsteadOfSkippingIt() {
        List<BigDecimal> values = Arrays.asList(
                BigDecimal.ONE,
                null,
                BigDecimal.valueOf(3)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateForDisplay(values)
        );
    }

    @Test
    void rejectsOutOfRangeGradeInsteadOfSkippingIt() {
        for (String invalid : List.of(
                "0", "0.99", "5.01", "9"
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> calculator.calculateForDisplay(
                            grades("2", invalid)
                    )
            );
        }
    }

    @Test
    void doesNotModifyInputList() {
        List<BigDecimal> input =
                new ArrayList<>(grades("1", "2", "3"));

        List<BigDecimal> original =
                new ArrayList<>(input);

        calculator.calculateForDisplay(input);

        assertEquals(original, input);
    }

    private List<BigDecimal> grades(String... values) {
        return Arrays.stream(values)
                .map(BigDecimal::new)
                .toList();
    }

    @Test
    void comparisonPreservesPrecisionBeyondDisplayScale() {
        List<BigDecimal> input = grades(
                "1", "1", "1", "1",
                "1", "1", "1", "2"
        );

        assertEquals(
                new BigDecimal("1.125"),
                calculator.calculateForComparison(input)
        );

        assertEquals(
                new BigDecimal("1.13"),
                calculator.calculateForDisplay(input)
        );
    }

    @Test
    void comparisonHandlesRepeatingDecimal() {
        assertEquals(
                new BigDecimal(
                        "1.666666666666666666666666666666667"
                ),
                calculator.calculateForComparison(
                        grades("1", "2", "2")
                )
        );
    }

    @Test
    void displayDoesNotDoubleRoundComparisonResult() {
        List<BigDecimal> input =
                grades("1.004" + "9".repeat(40));

        assertEquals(
                0,
                calculator.calculateForComparison(input)
                        .compareTo(new BigDecimal("1.005"))
        );

        assertEquals(
                new BigDecimal("1.00"),
                calculator.calculateForDisplay(input)
        );
    }

    @Test
    void comparisonRejectsInvalidGradesInsteadOfSkippingThem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateForComparison(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateForComparison(List.of())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateForComparison(
                        Arrays.asList(BigDecimal.ONE, null)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateForComparison(
                        grades("2", "5.01")
                )
        );
    }

    @Test
    void comparisonPreservesInputAndAcceptsBoundaries() {
        List<BigDecimal> input =
                new ArrayList<>(grades("1", "5"));

        List<BigDecimal> original =
                new ArrayList<>(input);

        assertEquals(
                0,
                calculator.calculateForComparison(input)
                        .compareTo(new BigDecimal("3"))
        );

        assertEquals(
                0,
                calculator.calculateForComparison(grades("1"))
                        .compareTo(BigDecimal.ONE)
        );

        assertEquals(
                0,
                calculator.calculateForComparison(grades("5"))
                        .compareTo(BigDecimal.valueOf(5))
        );

        assertEquals(original, input);
    }
}