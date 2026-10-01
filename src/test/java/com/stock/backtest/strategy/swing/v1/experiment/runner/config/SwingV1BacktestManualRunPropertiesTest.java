package com.stock.backtest.strategy.swing.v1.experiment.runner.config;

import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                        null,
                        null,
                        false
                );

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.candidateSymbols()).isEmpty();
    }

    @Test
    void rejectsMissingOrInvalidSettingsWhenEnabled() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SwingV1BacktestManualRunProperties(
                        true,
                        List.of("005930"),
                        null,
                        TO_SIGNAL_DATE,
                        "KOSPI",
                        10_000_000L,
                        costModel(),
                        false
                ))
                .withMessage("fromSignalDate must not be null.");
        assertThatThrownBy(() -> new SwingV1BacktestManualRunProperties(
                true,
                List.of("005930"),
                TO_SIGNAL_DATE,
                FROM_SIGNAL_DATE,
                "KOSPI",
                10_000_000L,
                costModel(),
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromSignalDate must not be after toSignalDate.");
        assertThatThrownBy(() -> new SwingV1BacktestManualRunProperties(
                true,
                List.of("005930"),
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                " ",
                10_000_000L,
                costModel(),
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benchmarkId must not be blank.");
        assertThatThrownBy(() -> new SwingV1BacktestManualRunProperties(
                true,
                List.of("005930"),
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                "KOSPI",
                null,
                costModel(),
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "initialCashAmountKrwPerSymbol must be positive."
                );
        assertThatNullPointerException()
                .isThrownBy(() -> new SwingV1BacktestManualRunProperties(
                        true,
                        List.of("005930"),
                        FROM_SIGNAL_DATE,
                        TO_SIGNAL_DATE,
                        "KOSPI",
                        10_000_000L,
                        null,
                        false
                ))
                .withMessage("costModel must not be null.");
    }

    @Test
    void rejectsMissingOrEmptyCandidatesWhenEnabled() {
        assertThatThrownBy(() -> enabledProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidateSymbols must not be empty when manual backtest is enabled.");
        assertThatThrownBy(() -> enabledProperties(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidateSymbols must not be empty when manual backtest is enabled.");
    }

    @Test
    void rejectsNullBlankAndDuplicateCandidatesWhenEnabled() {
        assertThatNullPointerException()
                .isThrownBy(() -> enabledProperties(Arrays.asList("005930", null)));
        assertThatThrownBy(() -> enabledProperties(List.of("005930", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidateSymbols must not contain blank symbols.");
        assertThatThrownBy(() -> enabledProperties(List.of("005930", "005930")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidateSymbols must not contain duplicate symbol: 005930");
    }

    @Test
    void copiesCandidatesWithoutChangingOrderOrLeadingZeros() {
        List<String> symbols = new ArrayList<>(List.of("005930", "000660"));
        SwingV1BacktestManualRunProperties properties = enabledProperties(symbols);
        symbols.clear();

        assertThat(properties.candidateSymbols()).containsExactly("005930", "000660");
        assertThatThrownBy(() -> properties.candidateSymbols().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private SwingV1BacktestManualRunProperties enabledProperties(List<String> symbols) {
        return new SwingV1BacktestManualRunProperties(
                true,
                symbols,
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                "KOSPI",
                10_000_000L,
                costModel(),
                false
        );
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
