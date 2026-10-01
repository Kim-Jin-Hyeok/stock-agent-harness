package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SwingV1BacktestExperimentRequest(
        InvestmentStrategyIdentity strategyIdentity,
        List<String> candidateSymbols,
        String benchmarkId,
        LocalDate fromSignalDate,
        LocalDate toSignalDate,
        long initialCashAmountKrwPerSymbol,
        TradeCostModel costModel
) {
    private static final String STRATEGY_ID = "SWING_V1";
    private static final int STRATEGY_VERSION = 1;

    public SwingV1BacktestExperimentRequest {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        if (!STRATEGY_ID.equals(strategyIdentity.strategyId())
                || strategyIdentity.strategyVersion() != STRATEGY_VERSION
                || strategyIdentity.horizon() != InvestmentHorizon.SWING) {
            throw new IllegalArgumentException(
                    "strategyIdentity must be SWING_V1 version 1."
            );
        }
        candidateSymbols = List.copyOf(Objects.requireNonNull(
                candidateSymbols,
                "candidateSymbols must not be null."
        ));
        if (candidateSymbols.isEmpty()) {
            throw new IllegalArgumentException(
                    "candidateSymbols must not be empty."
            );
        }
        Set<String> uniqueSymbols = new HashSet<>();
        for (String symbol : candidateSymbols) {
            if (symbol.isBlank()) {
                throw new IllegalArgumentException(
                        "candidateSymbols must not contain blank symbols."
                );
            }
            if (!uniqueSymbols.add(symbol)) {
                throw new IllegalArgumentException(
                        "candidateSymbols must not contain duplicate symbol: "
                                + symbol
                );
            }
        }
        if (benchmarkId == null || benchmarkId.isBlank()) {
            throw new IllegalArgumentException(
                    "benchmarkId must not be blank."
            );
        }
        Objects.requireNonNull(
                fromSignalDate,
                "fromSignalDate must not be null."
        );
        Objects.requireNonNull(
                toSignalDate,
                "toSignalDate must not be null."
        );
        if (fromSignalDate.isAfter(toSignalDate)) {
            throw new IllegalArgumentException(
                    "fromSignalDate must not be after toSignalDate."
            );
        }
        if (initialCashAmountKrwPerSymbol <= 0) {
            throw new IllegalArgumentException(
                    "initialCashAmountKrwPerSymbol must be positive."
            );
        }
        Objects.requireNonNull(costModel, "costModel must not be null.");
    }
}
