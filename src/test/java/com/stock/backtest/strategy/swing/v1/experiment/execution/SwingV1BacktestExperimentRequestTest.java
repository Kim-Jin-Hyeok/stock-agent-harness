package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingV1BacktestExperimentRequestTest {
    private static final String BENCHMARK_ID = "KOSPI";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final LocalDate FROM_SIGNAL_DATE =
            LocalDate.of(2026, 1, 2);
    private static final LocalDate TO_SIGNAL_DATE =
            LocalDate.of(2026, 6, 30);

    @Test
    void createsValidRequest() {
        TradeCostModel costModel = costModel();

        SwingV1BacktestExperimentRequest request =
                new SwingV1BacktestExperimentRequest(
                        STRATEGY_IDENTITY,
                        BENCHMARK_ID,
                        FROM_SIGNAL_DATE,
                        TO_SIGNAL_DATE,
                        10_000_000L,
                        costModel
                );

        assertThat(request.strategyIdentity())
                .isEqualTo(STRATEGY_IDENTITY);
        assertThat(request.benchmarkId()).isEqualTo(BENCHMARK_ID);
        assertThat(request.fromSignalDate())
                .isEqualTo(FROM_SIGNAL_DATE);
        assertThat(request.toSignalDate()).isEqualTo(TO_SIGNAL_DATE);
        assertThat(request.initialCashAmountKrwPerSymbol())
                .isEqualTo(10_000_000L);
        assertThat(request.costModel()).isSameAs(costModel);
    }

    @Test
    void rejectsIdentityOtherThanSwingV1() {
        InvestmentStrategyIdentity otherIdentity =
                new InvestmentStrategyIdentity(
                        "SWING_V2",
                        1,
                        InvestmentHorizon.SWING
                );

        assertThatThrownBy(() -> new SwingV1BacktestExperimentRequest(
                otherIdentity,
                BENCHMARK_ID,
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                10_000_000L,
                costModel()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "strategyIdentity must be SWING_V1 version 1."
                );
    }

    @Test
    void rejectsNullBenchmarkId() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentRequest(
                STRATEGY_IDENTITY,
                null,
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                10_000_000L,
                costModel()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benchmarkId must not be blank.");
    }

    @Test
    void rejectsBlankBenchmarkId() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentRequest(
                STRATEGY_IDENTITY,
                " ",
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                10_000_000L,
                costModel()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benchmarkId must not be blank.");
    }

    @Test
    void acceptsKosdaqBenchmarkId() {
        SwingV1BacktestExperimentRequest request =
                new SwingV1BacktestExperimentRequest(
                        STRATEGY_IDENTITY,
                        "KOSDAQ",
                        FROM_SIGNAL_DATE,
                        TO_SIGNAL_DATE,
                        10_000_000L,
                        costModel()
                );

        assertThat(request.benchmarkId()).isEqualTo("KOSDAQ");
    }

    @Test
    void rejectsReversedSignalDateRange() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentRequest(
                STRATEGY_IDENTITY,
                BENCHMARK_ID,
                TO_SIGNAL_DATE,
                FROM_SIGNAL_DATE,
                10_000_000L,
                costModel()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "fromSignalDate must not be after toSignalDate."
                );
    }

    @Test
    void rejectsNonPositivePerSymbolCash() {
        assertThatThrownBy(() -> new SwingV1BacktestExperimentRequest(
                STRATEGY_IDENTITY,
                BENCHMARK_ID,
                FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE,
                0L,
                costModel()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "initialCashAmountKrwPerSymbol must be positive."
                );
    }

    private TradeCostModel costModel() {
        return new TradeCostModel(
                "KIS_SIMULATION_V1",
                1,
                new BigDecimal("0.00015"),
                new BigDecimal("0.00015"),
                new BigDecimal("0.0018"),
                new BigDecimal("0.0010"),
                new BigDecimal("0.0010")
        );
    }
}
