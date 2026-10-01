package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
                        List.of("005930", "000660"),
                        BENCHMARK_ID,
                        FROM_SIGNAL_DATE,
                        TO_SIGNAL_DATE,
                        10_000_000L,
                        costModel
                );

        assertThat(request.strategyIdentity())
                .isEqualTo(STRATEGY_IDENTITY);
        assertThat(request.candidateSymbols()).containsExactly("005930", "000660");
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
                List.of("005930"),
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
                List.of("005930"),
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
                List.of("005930"),
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
                        List.of("005930"),
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
                List.of("005930"),
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
                List.of("005930"),
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

    @Test
    void copiesCandidateSymbolsWithoutChangingTheirOrder() {
        List<String> symbols = new ArrayList<>(List.of("000660", "005930"));
        SwingV1BacktestExperimentRequest request = request(symbols);
        symbols.clear();

        assertThat(request.candidateSymbols()).containsExactly("000660", "005930");
        assertThatThrownBy(() -> request.candidateSymbols().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingAndEmptyCandidateSymbols() {
        assertThatThrownBy(() -> request(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("candidateSymbols must not be null.");
        assertThatThrownBy(() -> request(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidateSymbols must not be empty.");
    }

    @Test
    void rejectsNullBlankAndDuplicateCandidateSymbols() {
        assertThatThrownBy(() -> request(java.util.Arrays.asList("005930", null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> request(List.of("005930", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidateSymbols must not contain blank symbols.");
        assertThatThrownBy(() -> request(List.of("005930", "005930")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidateSymbols must not contain duplicate symbol: 005930");
    }

    private SwingV1BacktestExperimentRequest request(List<String> symbols) {
        return new SwingV1BacktestExperimentRequest(
                STRATEGY_IDENTITY, symbols, BENCHMARK_ID, FROM_SIGNAL_DATE,
                TO_SIGNAL_DATE, 10_000_000L, costModel()
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
