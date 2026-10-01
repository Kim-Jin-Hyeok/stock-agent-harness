package com.stock.backtest.strategy.swing.v1.experiment.runner.config;

import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1BacktestManualRunPropertiesTest {
    private static final LocalDate FROM_SIGNAL_DATE =
            LocalDate.of(2026, 8, 3);
    private static final LocalDate TO_SIGNAL_DATE =
            LocalDate.of(2026, 8, 28);

    @Test
    void allowsDisabledRunnerWithoutExperimentSettings() {
        SwingV1BacktestManualRunProperties properties =
                new SwingV1BacktestManualRunProperties(
                        false,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertThat(properties.enabled()).isFalse();
    }

    @Test
    void rejectsMissingOrInvalidSettingsWhenEnabled() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SwingV1BacktestManualRunProperties(
                        true,
                        null,
                        TO_SIGNAL_DATE,
                        "KOSPI",
                        10_000_000L,
                        costModel()
                ))
                .withMessage("fromSignalDate must not be null.");
        assertThatThrownBy(() -> new SwingV1BacktestManualRunProperties(
                true,
                TO_SIGNAL_DATE,
                FROM_SIGNAL_DATE,
                "KOSPI",
                10_000_000L,
                costModel()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromSignalDate must not be after toSignalDate.");
        assertThatThrownBy(() -> new SwingV1BacktestManualRunProperties(
                true,
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                " ",
                10_000_000L,
                costModel()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benchmarkId must not be blank.");
        assertThatThrownBy(() -> new SwingV1BacktestManualRunProperties(
                true,
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                "KOSPI",
                null,
                costModel()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "initialCashAmountKrwPerSymbol must be positive."
                );
        assertThatNullPointerException()
                .isThrownBy(() -> new SwingV1BacktestManualRunProperties(
                        true,
                        FROM_SIGNAL_DATE,
                        TO_SIGNAL_DATE,
                        "KOSPI",
                        10_000_000L,
                        null
                ))
                .withMessage("costModel must not be null.");
    }

    private TradeCostModel costModel() {
        return new TradeCostModel(
                "TEST_COST_V1",
                1,
                new BigDecimal("0.00015"),
                new BigDecimal("0.00015"),
                new BigDecimal("0.0018"),
                new BigDecimal("0.001"),
                new BigDecimal("0.001")
        );
    }
}
