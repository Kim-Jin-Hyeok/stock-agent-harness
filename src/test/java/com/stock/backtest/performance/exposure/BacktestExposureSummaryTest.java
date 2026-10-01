package com.stock.backtest.performance.exposure;

import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestExposureSummaryTest {
    @Test
    void preservesValidRatesAndAcceptsDifferentDecimalScales() {
        BacktestExposureSummary summary = summary(4, 2, "0.500", "0.0750", "0.200");

        assertThat(summary.observationCount()).isEqualTo(4);
        assertThat(summary.investedObservationCount()).isEqualTo(2);
        assertThat(summary.investedObservationRate()).isEqualByComparingTo("0.5");
        assertThat(summary.averagePositionRatio()).isEqualByComparingTo("0.075");
        assertThat(summary.maxPositionRatio()).isEqualByComparingTo("0.2");
    }

    @Test
    void rejectsNonpositiveObservationCountAndInvalidInvestedCount() {
        for (int count : new int[]{0, -1}) {
            assertThatThrownBy(() -> summary(count, 0, "0", "0", "0"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("observationCount must be at least 1.");
        }
        for (int count : new int[]{-1, 5}) {
            assertThatThrownBy(() -> summary(4, count, "0.5", "0.075", "0.2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "investedObservationCount must be between 0 "
                                    + "and observationCount."
                    );
        }
    }

    @Test
    void rejectsNullRates() {
        assertThatThrownBy(() -> summary(4, 2, null, "0.075", "0.2"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("investedObservationRate must not be null.");
        assertThatThrownBy(() -> summary(4, 2, "0.5", null, "0.2"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("averagePositionRatio must not be null.");
        assertThatThrownBy(() -> summary(4, 2, "0.5", "0.075", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("maxPositionRatio must not be null.");
    }

    @Test
    void rejectsRatesOutsideZeroToOne() {
        for (String invalid : List.of("-0.01", "1.01")) {
            assertThatThrownBy(() -> summary(4, 2, invalid, "0.075", "0.2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("investedObservationRate must be between 0 and 1.");
            assertThatThrownBy(() -> summary(4, 2, "0.5", invalid, "0.2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("averagePositionRatio must be between 0 and 1.");
            assertThatThrownBy(() -> summary(4, 2, "0.5", "0.075", invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("maxPositionRatio must be between 0 and 1.");
        }
    }

    @Test
    void rejectsInvestedRateInconsistentWithCounts() {
        assertThatThrownBy(() -> summary(4, 2, "0.25", "0.075", "0.2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("investedObservationRate must match observation counts.");
    }

    @Test
    void rejectsAverageAboveMaximumOrInvestedObservationRate() {
        assertThatThrownBy(() -> summary(4, 2, "0.5", "0.3", "0.2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> summary(4, 1, "0.25", "0.3", "0.5"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPositivePositionRatioWithoutInvestedObservations() {
        assertThatThrownBy(() -> summary(4, 0, "0", "0", "0.2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cash-only exposure must have zero position ratios.");
    }

    @Test
    void rejectsZeroPositionRatiosWhenObservationsAreInvested() {
        assertThatThrownBy(() -> summary(4, 2, "0.5", "0", "0.2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invested exposure must have positive position ratios.");
        assertThatThrownBy(() -> summary(4, 2, "0.5", "0", "0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesAllSummaryFieldsAgainstOriginalCurve() {
        List<BacktestEquitySnapshot> curve = curve();
        BacktestExposureSummary correct = summary(2, 1, "0.5", "0.05", "0.1");

        assertThatCode(() -> correct.validateAgainst(curve)).doesNotThrowAnyException();
        for (BacktestExposureSummary incorrect : List.of(
                summary(4, 2, "0.5", "0.05", "0.1"),
                summary(2, 2, "1", "0.05", "0.1"),
                summary(2, 1, "0.5", "0.04", "0.1"),
                summary(2, 1, "0.5", "0.05", "0.2")
        )) {
            assertThatThrownBy(() -> incorrect.validateAgainst(curve))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("exposureSummary must match equityCurve.");
        }
    }

    private List<BacktestEquitySnapshot> curve() {
        LocalDate first = LocalDate.of(2026, 9, 1);
        LocalDate second = first.plusDays(1);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        return List.of(
                new BacktestEquitySnapshot(
                        first, first.atTime(9, 10).atZone(zone).toInstant(),
                        1_000L, 0L, 1_000L
                ),
                new BacktestEquitySnapshot(
                        second, second.atTime(9, 10).atZone(zone).toInstant(),
                        900L, 100L, 1_000L
                )
        );
    }

    private BacktestExposureSummary summary(
            int count, int invested, String rate, String average, String max
    ) {
        return new BacktestExposureSummary(
                count, invested, decimal(rate), decimal(average), decimal(max)
        );
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
