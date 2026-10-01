package com.stock.backtest.performance.exposure;

import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestExposureCalculatorTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 1);

    @Test
    void cashOnlyCurveHasZeroExposure() {
        BacktestExposureSummary summary = BacktestExposureCalculator.calculate(
                List.of(snapshot(0, 1_000L, 0L), snapshot(1, 1_000L, 0L))
        );

        assertThat(summary.observationCount()).isEqualTo(2);
        assertThat(summary.investedObservationCount()).isZero();
        assertThat(summary.investedObservationRate()).isEqualByComparingTo("0");
        assertThat(summary.averagePositionRatio()).isEqualByComparingTo("0");
        assertThat(summary.maxPositionRatio()).isEqualByComparingTo("0");
    }

    @Test
    void fullyInvestedCurveHasOneExposureEvenWhenAssetsChange() {
        BacktestExposureSummary summary = BacktestExposureCalculator.calculate(
                List.of(snapshot(0, 0L, 100L), snapshot(1, 0L, 50L))
        );

        assertThat(summary.investedObservationCount()).isEqualTo(2);
        assertThat(summary.investedObservationRate()).isEqualByComparingTo("1");
        assertThat(summary.averagePositionRatio()).isEqualByComparingTo("1");
        assertThat(summary.maxPositionRatio()).isEqualByComparingTo("1");
    }

    @Test
    void includesCashOnlyObservationsInAverageAfterBuyAndSell() {
        BacktestExposureSummary summary = BacktestExposureCalculator.calculate(
                List.of(
                        snapshot(0, 1_000L, 0L),
                        snapshot(1, 900L, 100L),
                        snapshot(2, 800L, 200L),
                        snapshot(3, 1_000L, 0L)
                )
        );

        assertThat(summary.observationCount()).isEqualTo(4);
        assertThat(summary.investedObservationCount()).isEqualTo(2);
        assertThat(summary.investedObservationRate()).isEqualByComparingTo("0.5");
        assertThat(summary.averagePositionRatio()).isEqualByComparingTo("0.075");
        assertThat(summary.maxPositionRatio()).isEqualByComparingTo("0.2");
    }

    @Test
    void averagesIndividualRatiosRatherThanAggregatingAssetAmounts() {
        BacktestExposureSummary summary = BacktestExposureCalculator.calculate(
                List.of(snapshot(0, 100L, 100L), snapshot(1, 9_900L, 100L))
        );

        assertThat(summary.averagePositionRatio()).isEqualByComparingTo("0.255");
        assertThat(summary.maxPositionRatio()).isEqualByComparingTo("0.5");
        assertThat(summary.investedObservationRate()).isEqualByComparingTo("1");
    }

    @Test
    void weightsValuationDatesEquallyRatherThanCalendarDays() {
        BacktestExposureSummary summary = BacktestExposureCalculator.calculate(
                List.of(snapshot(0, 1_000L, 0L), snapshot(10, 900L, 100L))
        );

        assertThat(summary.observationCount()).isEqualTo(2);
        assertThat(summary.investedObservationRate()).isEqualByComparingTo("0.5");
        assertThat(summary.averagePositionRatio()).isEqualByComparingTo("0.05");
    }

    @Test
    void handlesSingleInvestedObservationAndDecimalDivision() {
        BacktestExposureSummary summary = BacktestExposureCalculator.calculate(
                List.of(snapshot(0, 2L, 1L))
        );

        assertThat(summary.observationCount()).isEqualTo(1);
        assertThat(summary.investedObservationRate()).isEqualByComparingTo("1");
        assertThat(summary.averagePositionRatio())
                .isEqualByComparingTo("0.3333333333333333333333333333333333");
        assertThat(summary.maxPositionRatio()).isEqualByComparingTo(
                summary.averagePositionRatio()
        );
    }

    @Test
    void doesNotOverflowWhenRepeatedValuationsUseMaximumLongAssets() {
        BacktestExposureSummary summary = BacktestExposureCalculator.calculate(
                List.of(
                        snapshot(0, 0L, Long.MAX_VALUE),
                        snapshot(1, 0L, Long.MAX_VALUE)
                )
        );

        assertThat(summary.averagePositionRatio()).isEqualByComparingTo("1");
        assertThat(summary.maxPositionRatio()).isEqualByComparingTo("1");
    }

    @Test
    void calculationDoesNotModifyInputCurve() {
        List<BacktestEquitySnapshot> curve = new ArrayList<>(List.of(
                snapshot(0, 1_000L, 0L), snapshot(1, 900L, 100L)
        ));
        List<BacktestEquitySnapshot> before = List.copyOf(curve);

        BacktestExposureSummary summary = BacktestExposureCalculator.calculate(curve);

        assertThat(curve).containsExactlyElementsOf(before);
        curve.clear();
        assertThat(summary.observationCount()).isEqualTo(2);
        assertThat(summary.averagePositionRatio()).isEqualByComparingTo("0.05");
    }

    @Test
    void rejectsNullCurveAndNullObservation() {
        assertThatThrownBy(() -> BacktestExposureCalculator.calculate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("equityCurve must not be null.");
        List<BacktestEquitySnapshot> curve = new ArrayList<>();
        curve.add(snapshot(0, 1_000L, 0L));
        curve.add(null);

        assertThatThrownBy(() -> BacktestExposureCalculator.calculate(curve))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsEmptyCurve() {
        assertThatThrownBy(() -> BacktestExposureCalculator.calculate(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("equityCurve must not be empty.");
    }

    @Test
    void rejectsDuplicateAndReversedValuationDates() {
        for (List<BacktestEquitySnapshot> curve : List.of(
                List.of(snapshot(0, 1_000L, 0L), snapshot(0, 900L, 100L)),
                List.of(snapshot(1, 1_000L, 0L), snapshot(0, 900L, 100L))
        )) {
            assertThatThrownBy(() -> BacktestExposureCalculator.calculate(curve))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("equityCurve must be ordered by unique valuationDate.");
        }
    }

    @Test
    void rejectsZeroAssetsInsteadOfSilentlyTreatingThemAsCashOnly() {
        assertThatThrownBy(() -> BacktestExposureCalculator.calculate(List.of(
                snapshot(0, 1_000L, 0L), snapshot(1, 0L, 0L)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Exposure calculation requires positive total assets "
                                + "at every valuation."
                );
    }

    private BacktestEquitySnapshot snapshot(int offset, long cash, long position) {
        LocalDate date = FIRST_DATE.plusDays(offset);
        return new BacktestEquitySnapshot(
                date,
                date.atTime(9, 10).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                cash,
                position,
                Math.addExact(cash, position)
        );
    }
}
