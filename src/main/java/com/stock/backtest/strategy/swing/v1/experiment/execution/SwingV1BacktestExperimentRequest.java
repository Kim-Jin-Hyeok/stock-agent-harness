package com.stock.backtest.strategy.swing.v1.experiment.execution;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;

import java.time.LocalDate;
import java.util.Objects;

public record SwingV1BacktestExperimentRequest(
        InvestmentStrategyIdentity strategyIdentity,
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
