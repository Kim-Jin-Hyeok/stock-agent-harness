package com.stock.backtest.strategy.swing.v1.experiment.summary;

import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SwingV1BacktestExperimentSummaryTest {
    private final SwingV1BacktestExperimentRequest request =
            mock(SwingV1BacktestExperimentRequest.class);

    @Test
    void createsValidSummary() {
        SwingV1BacktestExperimentSummary summary =
                new SwingV1BacktestExperimentSummary(
                        request,
                        3,
                        1,
                        1,
                        1,
                        1,
                        3,
                        BigDecimal.ZERO,
                        new BigDecimal("0.1"),
                        new BigDecimal("-0.1"),
                        1,
                        "000660",
                        new BigDecimal("-0.2"),
                        "000660",
                        new BigDecimal("0.3")
                );

        assertThat(summary.request()).isSameAs(request);
        assertThat(summary.symbolCount()).isEqualTo(3);
        assertThat(summary.profitableSymbolCount()).isEqualTo(1);
        assertThat(summary.losingSymbolCount()).isEqualTo(1);
        assertThat(summary.breakEvenSymbolCount()).isEqualTo(1);
        assertThat(summary.noCompletedTradeSymbolCount()).isEqualTo(1);
        assertThat(summary.totalCompletedTradeCount()).isEqualTo(3);
        assertThat(summary.medianLiquidationAdjustedReturnRate())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.benchmarkTotalReturnRate())
                .isEqualByComparingTo(new BigDecimal("0.1"));
        assertThat(summary.medianExcessReturnRate())
                .isEqualByComparingTo(new BigDecimal("-0.1"));
        assertThat(summary.benchmarkOutperformingSymbolCount()).isEqualTo(1);
        assertThat(summary.worstReturnSymbol()).isEqualTo("000660");
        assertThat(summary.worstLiquidationAdjustedReturnRate())
                .isEqualByComparingTo(new BigDecimal("-0.2"));
        assertThat(summary.worstDrawdownSymbol()).isEqualTo("000660");
        assertThat(summary.worstMaxDrawdownRate())
                .isEqualByComparingTo(new BigDecimal("0.3"));
    }

    @Test
    void rejectsMismatchedOutcomeCounts() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentSummary(
                request,
                3,
                1,
                1,
                0,
                1,
                3,
                BigDecimal.ZERO,
                new BigDecimal("0.1"),
                new BigDecimal("-0.1"),
                1,
                "000660",
                new BigDecimal("-0.2"),
                "000660",
                new BigDecimal("0.3")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "symbolCount must match classified symbol counts."
                );
    }

    @Test
    void rejectsCompletedTradesWhenEverySymbolHasNoCompletedTrade() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentSummary(
                request,
                2,
                1,
                1,
                0,
                2,
                1,
                BigDecimal.ZERO,
                new BigDecimal("0.1"),
                new BigDecimal("-0.1"),
                1,
                "000660",
                new BigDecimal("-0.2"),
                "000660",
                new BigDecimal("0.3")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Completed trade counts must match no-trade symbols."
                );
    }

    @Test
    void rejectsWorstReturnAboveMedian() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentSummary(
                request,
                1,
                1,
                0,
                0,
                0,
                1,
                new BigDecimal("0.1"),
                new BigDecimal("0.1"),
                BigDecimal.ZERO,
                1,
                "005930",
                new BigDecimal("0.2"),
                "005930",
                new BigDecimal("0.1")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "worstLiquidationAdjustedReturnRate must not "
                                + "exceed the median."
                );
    }

    @Test
    void rejectsOutperformingCountAboveSymbolCount() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentSummary(
                request,
                1,
                1,
                0,
                0,
                0,
                1,
                new BigDecimal("0.2"),
                new BigDecimal("0.1"),
                new BigDecimal("0.1"),
                2,
                "005930",
                new BigDecimal("0.2"),
                "005930",
                new BigDecimal("0.1")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "benchmarkOutperformingSymbolCount must be "
                                + "between 0 and symbolCount."
                );
    }

    @Test
    void rejectsInconsistentMedianExcessReturnRate() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentSummary(
                request,
                1,
                1,
                0,
                0,
                0,
                1,
                new BigDecimal("0.2"),
                new BigDecimal("0.1"),
                BigDecimal.ZERO,
                1,
                "005930",
                new BigDecimal("0.2"),
                "005930",
                new BigDecimal("0.1")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "medianExcessReturnRate must match the median "
                                + "return minus benchmark return."
                );
    }
}
